(ns metabase.server.middleware.auth
  "Middleware related to enforcing authentication/API keys (when applicable). Unlike most other middleware most of this
  is not used as part of the normal `app`; it is instead added selectively to appropriate routes."
  (:require
   [buddy.sign.jwt :as jwt]
   [buddy.sign.jwt.verify :as jwt-verify]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str]
   [metabase.integrations.openid :as openid]
   [metabase.models.setting :refer [defsetting]]
   [metabase.server.middleware.util :as mw.util]
   [metabase.util.i18n :refer [deferred-trs]]
   [metabase.util.log :as log]
   [ring.util.codec :as codec]))

(def ^:private ^:const ^String metabase-api-key-header "x-metabase-apikey")

(defn enforce-authentication
  "Middleware that returns a 401 response if `request` has no associated `:metabase-user-id`."
  [handler]
  (fn [{:keys [metabase-user-id] :as request} respond raise]
    (if metabase-user-id
      (handler request respond raise)
      (respond mw.util/response-unauthentic))))

(defn- wrap-api-key* [{:keys [headers], :as request}]
  (if-let [api-key (headers metabase-api-key-header)]
    (assoc request :metabase-api-key api-key)
    request))

(defn wrap-api-key
  "Middleware that sets the `:metabase-api-key` keyword on the request if a valid API Key can be found. We check the
  request headers for `X-METABASE-APIKEY` and if it's not found then no keyword is bound to the request."
  [handler]
  (fn [request respond raise]
    (handler (wrap-api-key* request) respond raise)))

(defsetting api-key
  "When set, this API key is required for all API requests."
  :visibility :internal)

(def mb-api-key-doc-url
  "Url for documentation on how to set MB_API_KEY."
  "https://www.metabase.com/docs/latest/configuring-metabase/environment-variables#mb_api_key")

(def key-not-set-response
  "Response when the MB_API_KEY is not set."
  {:status 403
   :body (deferred-trs "MB_API_KEY is not set. See {0} for details" mb-api-key-doc-url)})

(defn enforce-api-key
  "Middleware that enforces validation of the client via API Key, canceling the request processing if the check fails.

  Validation is handled by first checking for the presence of the `:metabase-api-key` on the request.  If the api key
  is available then we validate it by checking it against the configured `:mb-api-key` value set in our global config.

  If the request `:metabase-api-key` matches the configured `api-key` value then the request continues, otherwise we
  reject the request and return a 403 Forbidden response.

  This variable only works for /api/notify/db/:id endpoint"
  [handler]
  (fn [{:keys [metabase-api-key], :as request} respond raise]
    (cond (str/blank? (api-key))
          (respond key-not-set-response)

          (not metabase-api-key)
          (respond mw.util/response-forbidden)

          (= (api-key) metabase-api-key)
          (handler request respond raise)

          :else
          (respond mw.util/response-forbidden))))

(defn- fetch-jwks
  "Получает JWKS (JSON Web Key Set) из указанного URL"
  [jwks-uri]
  (try
    (log/info "Получение JWKS из:" jwks-uri)
    (let [response (http/get jwks-uri
                             {:throw-exceptions false
                              :insecure?        true
                              :accept           :json})]
      (if (= 200 (:status response))
        (json/parse-string (:body response) true)
        (do
          (log/error "Ошибка получения JWKS, статус:" (:status response))
          (throw (ex-info "Не удалось получить JWKS" {:status (:status response)})))))
    (catch Exception e
      (log/error e "Исключение при получении JWKS из:" jwks-uri)
      (throw e))))

(defn- jwt-header
  "Извлекает заголовок JWT токена"
  [^String token]
  (try
    (let [[header] (str/split token #"\.")]
      (json/parse-string (String. (codec/base64-decode header)) keyword))
    (catch Exception e
      (log/error e "Ошибка при извлечении заголовка JWT")
      nil)))

(defn- jwks-key->public-key
  "Конвертирует JWKS ключ в публичный ключ для buddy-sign"
  [jwks-key]
  (try
    (let [n (:n jwks-key)  ; modulus
          e (:e jwks-key)  ; exponent
          ;; JWKS использует URL-safe base64, нужно заменить - на + и _ на /
          n-safe (str/replace (str/replace n "-" "+") "_" "/")
          e-safe (str/replace (str/replace e "-" "+") "_" "/")
          ;; Добавляем padding если нужно
          n-padded (if (zero? (mod (count n-safe) 4))
                     n-safe
                     (str n-safe (str/join (repeat (- 4 (mod (count n-safe) 4)) "="))))
          e-padded (if (zero? (mod (count e-safe) 4))
                     e-safe
                     (str e-safe (str/join (repeat (- 4 (mod (count e-safe) 4)) "="))))
          n-bytes (codec/base64-decode n-padded)
          e-bytes (codec/base64-decode e-padded)]
      (log/info "Конвертация JWKS ключа в публичный ключ")
      (log/debug "Исходный n:" n)
      (log/debug "Исходный e:" e)
      (log/debug "Преобразованный n:" n-padded)
      (log/debug "Преобразованный e:" e-padded)
      ;; Создаем RSA публичный ключ из модуля и экспоненты
      (let [spec (java.security.spec.RSAPublicKeySpec.
                   (java.math.BigInteger. 1 n-bytes)
                   (java.math.BigInteger. 1 e-bytes))
            key-factory (java.security.KeyFactory/getInstance "RSA")]
        (.generatePublic key-factory spec)))
    (catch Exception e
      (log/error e "Ошибка при конвертации JWKS ключа в публичный ключ")
      (throw e))))

(defn- get-signing-key-from-jwt
  "Получает подписывающий ключ из JWKS на основе JWT токена (аналогично Python jwt.JWKClient.get_signing_key_from_jwt)"
  [token jwks-uri]
  (try
    (let [jwks (fetch-jwks jwks-uri)
          keys (:keys jwks)
          header (jwt-header token)
          kid (:kid header)]

      (log/info "Получено" (count keys) "ключей из JWKS")
      (log/info "Key ID из JWT заголовка:" kid)

      (if kid
        (let [signing-key (first (filter #(= (:kid %) kid) keys))]
          (if signing-key
            (do
              (log/info "Найден подписывающий ключ для kid:" kid)
              (let [public-key (jwks-key->public-key signing-key)]
                {:valid true
                 :token token
                 :jwks-uri jwks-uri
                 :signing-key public-key
                 :kid kid}))
            (do
              (log/error "Ключ с kid" kid "не найден в JWKS")
              {:valid false
               :error (str "Ключ с kid " kid " не найден в JWKS")
               :token token
               :jwks-uri jwks-uri
               :kid kid})))
        (do
          (log/error "JWT заголовок не содержит kid")
          {:valid false
           :error "JWT заголовок не содержит kid"
           :token token
           :jwks-uri jwks-uri})))
    (catch Exception e
      (log/error e "Ошибка при получении подписывающего ключа из JWT")
      {:valid false
       :error (.getMessage e)
       :token token
       :jwks-uri jwks-uri})))

(defn- validate-jwt-token
  "Валидирует JWT токен используя JWKS"
  [token jwks-uri]
  (let [result (get-signing-key-from-jwt token jwks-uri)]
    (if (:valid result)
      (try
        (let [signing-key (:signing-key result)
              header (jwt-header token)
              algorithm (:alg header)
              ;; Преобразуем алгоритм в формат, который понимает buddy-sign
              alg-key (keyword (str/lower-case algorithm))]
          (log/info "Валидация JWT токена с алгоритмом:" algorithm)
          (log/debug "Используемый алгоритм:" alg-key)
          (log/debug "Тип signing-key:" (type signing-key))
          (log/debug "Signing-key:" signing-key)
          ;; Используем jwt-verify/verify для валидации JWT
          (let [decoded-token (jwt-verify/verify token signing-key {:alg alg-key})]
            (log/info "JWT токен успешно расшифрован и валидирован")
            (log/info "JWT payload:" decoded-token)
            (assoc result :decoded-token decoded-token)))
        (catch Exception e
          (log/error e "Ошибка при расшифровке JWT токена")
          {:valid false
           :error (str "Ошибка расшифровки JWT: " (.getMessage e))
           :token token
           :jwks-uri jwks-uri}))
      (do
        (log/error "JWT токен невалиден:" (:error result))
        result))))

(defn- wrap-openid-token* [{:keys [headers], :as request}]
  (if-let [auth-header (get headers "authorization")]
    (let [token (when (str/starts-with? auth-header "Bearer ")
                  (subs auth-header 7))]
      (if token
        (do
          (log/infof "Получен OpenID токен: %s" token)

          ;; Получаем конфигурацию OpenID и валидируем токен
          (try
            (let [discovery-config (openid/fetch-openid-discovery-config)
                  jwks-uri (:jwks_uri discovery-config)]
              (when jwks-uri
                (let [validation-result (validate-jwt-token token jwks-uri)]
                  (log/info "Результат валидации JWT:" validation-result))))
            (catch Exception e
              (log/error e "Ошибка при получении OpenID конфигурации для валидации токена")))

          (assoc request :openid-token token))
        request))
    request))

(defn wrap-openid-token
  "Middleware that извлекает OpenID токен из заголовка Authorization и логирует его.
  Токен должен быть в формате 'Bearer <token>'."
  [handler]
  (fn [request respond raise]
    (handler (wrap-openid-token* request) respond raise)))
