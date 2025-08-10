(ns metabase.integrations.twork
    "TWork Connect integration for Metabase"
    (:require
      [cheshire.core :as json]
      [buddy.sign.jwt :as jwt]
      [clj-http.client :as http]
      [clojure.string :as str]
      [metabase.api.common :as api]
      [java-time :as t]
      [metabase.api.session :as api.session]
      [metabase.models.interface :as mi]
      [metabase.models.setting :as setting
       :refer                      [defsetting]]
      [metabase.models.user :as user
       :refer                   [User]]
      [metabase.util :as u]
      [metabase.util.i18n :refer [deferred-tru tru]]
      [metabase.util.log :as log]
      [metabase.util.schema :as su]
      [schema.core :as s]
      [toucan.db :as db]
      [buddy.sign.jwt :as jwt]
      [cheshire.core :as json]
      [clj-http.client :as http]
      [clojure.string :as str]
      [java-time :as t]
      [metabase.api.session :as api.session]
      [metabase.models.session :refer [Session]]
      [metabase.models.setting :refer [defsetting]]
      [metabase.models.user :as user :refer [User]]
      [metabase.server.middleware.session :as mw.session]
      [metabase.server.middleware.util :as mw.util]
      [metabase.server.request.util :as request.u]
      [metabase.util :as u]
      [metabase.util.i18n :refer [deferred-trs]]
      [metabase.util.log :as log]
      [metabase.util.schema :as su]
      [schema.core :as s]
      [toucan.db :as db])
    (:import
      (java.net URL)
      (java.util UUID)))

(set! *warn-on-reflection* true)

;;; ======================================= Settings =======================================

(defsetting twork-auth-enabled
  (deferred-tru "Is TWork Connect authentication currently enabled?")
  :type :boolean
  :visibility :public
  :default false)

(defsetting twork-auth-config-url
  (deferred-tru "TWork Connect configuration URL.")
  :type :string)

(defsetting twork-auth-issuer
  (deferred-tru "TWork Connect issuer identifier.")
  :type :string)

(defsetting twork-auth-client-id
  (deferred-tru "TWork Connect client identifier.")
  :type :string)

(defsetting twork-auth-redirect-uri
  (deferred-tru "TWork Connect redirect URI.")
  :type :string)

(defsetting twork-auth-response-type
  (deferred-tru "TWork Connect response type.")
  :type :string
  :default "code")

(defsetting twork-auth-scope
  (deferred-tru "TWork Connect scope.")
  :type :string
  :default "twork profile offline_access")

(defsetting twork-auth-grant-type
  (deferred-tru "TWork Connect grant type.")
  :type :string
  :default "authorization_code")

(defsetting twork-auth-people-hub-client-secret
  (deferred-tru "PeopleHub client secret.")
  :type :string)

(defsetting twork-auth-people-hub-client-id
  (deferred-tru "PeopleHub client ID.")
  :type :string)

(defsetting twork-auth-people-hub-auth-token-host
  (deferred-tru "PeopleHub auth token host.")
  :type :string)

(defsetting twork-auth-people-hub-scope
  (deferred-tru "PeopleHub scope.")
  :type :string
  :default "hrp_public_api hrp_employee_reader_public")

(defsetting twork-auth-people-hub-employee-reader-host
  (deferred-tru "PeopleHub EmployeeReader host.")
  :type :string)

(defsetting twork-auth-configured
  (deferred-tru "Is TWork Connect configured?")
  :type :boolean
  :visibility :public
  :setter :none
  :getter
  (fn []
    (boolean
     (and (twork-auth-config-url)
          (twork-auth-issuer)
          (twork-auth-client-id)
          (twork-auth-redirect-uri)
          (twork-auth-response-type)
          (twork-auth-scope)
          (twork-auth-grant-type)
          (twork-auth-people-hub-client-secret)
          (twork-auth-people-hub-client-id)
          (twork-auth-people-hub-auth-token-host)
          (twork-auth-people-hub-scope)
          (twork-auth-people-hub-employee-reader-host)))))

;;; ======================================= Core Functions =======================================

(defn twork-enabled?
  "Is TWork Connect authentication currently enabled?"
  []
  (twork-auth-enabled))

(defn fetch-twork-configuration
  "Fetch TWork Connect configuration from the discovery endpoint."
  [config-url]
  (try
    (log/info "Fetching TWork configuration from:" config-url)
    (let [response (http/get config-url
                             {:throw-exceptions false
                              :insecure?        true
                              ; Allow insecure SSL for testing
                              :accept           :json})]
      (log/info "TWork configuration response status:" (:status response))
      (if (= 200 (:status response))
        (json/parse-string (:body response) true)
        (do
          (log/error "TWork configuration failed with status:" (:status response) "body:" (:body response))
          (throw
            (ex-info (tru "Failed to fetch TWork configuration")
                     {:status (:status response)
                      :body   (:body response)})))))
    (catch Exception e
      (log/error e "Exception while fetching TWork configuration from:" config-url)
      (throw
        (ex-info (tru "Error fetching TWork configuration: {0}" (.getMessage e))
                 {:error e})))))

(defn build-authorization-url
  "Build the TWork Connect authorization URL with required parameters."
  [authorization-endpoint client-id redirect-uri response-type scope]
  (let [params {:client_id     client-id
                :redirect_uri  redirect-uri
                :response_type response-type
                :scope         scope}]
    (str authorization-endpoint "?" (http/generate-query-string params))))

(defn fetch-auth-url
  "Initiate TWork Connect authentication flow."
  []
  (when-not (twork-enabled?)
            (throw
              (ex-info (tru "TWork Connect is not enabled")
                       {:status-code 400})))

  (let [config-url    (twork-auth-config-url)
        client-id     (twork-auth-client-id)
        redirect-uri  (twork-auth-redirect-uri)
        response-type (twork-auth-response-type)
        scope         (twork-auth-scope)]

    (when-not (and config-url client-id redirect-uri)
              (throw
                (ex-info (tru "TWork Connect is not properly configured")
                         {:status-code 400})))

    (try
      (let [config                 (fetch-twork-configuration config-url)
            authorization-endpoint (:authorization_endpoint config)]

        (when-not authorization-endpoint
                  (throw
                    (ex-info (tru "Authorization endpoint not found in TWork configuration")
                             {:status-code 400})))

        (let [authorization-url (build-authorization-url
                                 authorization-endpoint
                                 client-id
                                 redirect-uri
                                 response-type
                                 scope)]

          {:authorization_url authorization-url}))
      (catch Exception e
        (log/error e "Error initiating TWork authentication")
        (throw
          (ex-info (tru "Failed to initiate TWork authentication: {0}" (.getMessage e))
                   {:status-code 500
                    :error       e}))))))

(defn validate-twork-settings
  "Validate TWork Connect settings by testing the configuration URL."
  [config-url issuer client-id]
  (try
    (let [config (fetch-twork-configuration config-url)]
      (log/info "TWork configuration fetched successfully:" (keys config))
      (cond
       (not= issuer (:issuer config))
       {:status  :ERROR
        :message (tru "Issuer mismatch. Expected: {0}, Got: {1}" issuer (:issuer config))}

       (not (:authorization_endpoint config))
       {:status  :ERROR
        :message (tru "Missing authorization endpoint in TWork configuration")}

       (not (:token_endpoint config))
       {:status  :ERROR
        :message (tru "Missing token endpoint in TWork configuration")}

       :else
       {:status :SUCCESS}))
    (catch Exception e
      {:status  :ERROR
       :message (.getMessage e)})))

(defn twork-settings
  "A map of all TWork Connect settings"
  []
  {:config-url                      (twork-auth-config-url)
   :issuer                          (twork-auth-issuer)
   :client-id                       (twork-auth-client-id)
   :redirect-uri                    (twork-auth-redirect-uri)
   :response-type                   (twork-auth-response-type)
   :scope                           (twork-auth-scope)
   :grant-type                      (twork-auth-grant-type)
   :people-hub-client-secret        (twork-auth-people-hub-client-secret)
   :people-hub-client-id            (twork-auth-people-hub-client-id)
   :people-hub-auth-token-host      (twork-auth-people-hub-auth-token-host)
   :people-hub-scope                (twork-auth-people-hub-scope)
   :people-hub-employee-reader-host (twork-auth-people-hub-employee-reader-host)})

(defn fetch-twork-discovery-config
  "Получает конфигурацию TWork Connect из discovery endpoint и возвращает ключевые параметры.
   Возвращает словарь с ключами: :issuer, :id_token_signing_alg_values_supported, :jwks_uri"
  []
  (try
    (let [config-url (twork-auth-config-url)]
      (when-not config-url
        (throw (ex-info (tru "TWork config URL не настроен") {:status-code 400})))

      (log/info "Получение TWork discovery конфигурации из:" config-url)
      (let [config (fetch-twork-configuration config-url)]
        {:issuer                              (:issuer config)
         :id_token_signing_alg_values_supported (:id_token_signing_alg_values_supported config)
         :jwks_uri                            (:jwks_uri config)}))
    (catch Exception e
      (log/error e "Ошибка при получении TWork discovery конфигурации")
      (throw (ex-info (tru "Не удалось получить TWork discovery конфигурацию: {0}" (.getMessage e))
                      {:status-code 500
                       :error       e})))))

;;; ======================================= User Management =======================================


(defn fetch-access-token
  [code]
  (when-not (twork-enabled?)
            (throw
              (ex-info (tru "TWork Connect is not enabled")
                       {:status-code 400})))

  (when-not code
            (throw
              (ex-info (tru "Authorization code is required")
                       {:status-code 400})))

  (try
    (let [config-url     (twork-auth-config-url)
          client-id      (twork-auth-client-id)
          grant-type     (twork-auth-grant-type)
          redirect-uri   (twork-auth-redirect-uri)
          config         (fetch-twork-configuration config-url)
          token-endpoint (:token_endpoint config)]

      (when-not token-endpoint
                (throw
                  (ex-info (tru "Token endpoint not found in TWork configuration")
                           {:status-code 400})))

      ;; Exchange authorization code for tokens
      (let [token-response (http/post token-endpoint
                                      {:form-params      {:grant_type   grant-type
                                                          :client_id    client-id
                                                          :code         code
                                                          :redirect_uri redirect-uri}
                                       :throw-exceptions false
                                       :insecure?        true})]

        (if (= 200 (:status token-response))
          (let [token-data   (json/parse-string (:body token-response) true)
                access-token (:access_token token-data)]
            access-token)
          (do
            (log/error "Token exchange failed with status:" (:status token-response) "body:" (:body token-response))
            (throw
              (ex-info (tru "Failed to exchange authorization code for tokens")
                       {:status-code 400
                        :body        (:body token-response)}))))))
    (catch Exception e
      (log/error e "Error during TWork authentication")
      (throw
        (ex-info (tru "TWork authentication failed: {0}" (.getMessage e))
                 {:status-code 500
                  :error       e})))))



(defn- fetch-jwks
  "Получает JWKS (JSON Web Key Set) из указанного URL"
  [jwks-uri]
  (try
    (let [response (http/get jwks-uri
                             {:throw-exceptions false
                              :insecure?        true
                              :accept           :json})]
      (if (= 200 (:status response))
        (json/parse-string (:body response) true)
        (do
          (throw (ex-info "Не удалось получить JWKS" {:status (:status response)})))))
    (catch Exception e
      (throw e))))

(defn pad-base64url [s]
  (case (mod (count s) 4)
    2 (str s "==")
    3 (str s "=")
    0 s
    (str s)))

(defn jwt-header [^String token]
  (try
    (let [[header] (str/split token #"\.")]
      (-> header
          pad-base64url
          (.getBytes "UTF-8")
          ((fn [bytes] (.decode (java.util.Base64/getUrlDecoder) bytes)))
          (String. "UTF-8")
          (json/parse-string keyword)))
    (catch Exception e
      nil)))

(defn from-base64url-uint [s]
  (let [decoder (java.util.Base64/getUrlDecoder)
        ;; Добавляем padding вручную
        padded (case (mod (count s) 4)
                 2 (str s "==")
                 3 (str s "=")
                 0 s
                 (throw (IllegalArgumentException. "Invalid base64url string length")))
        bytes (.decode decoder padded)]
    (BigInteger. 1 bytes))) ;; 1 = unsigneds


(defn- jwks-key->public-key
  "Конвертирует JWKS ключ в публичный ключ для buddy-sign"
  [jwks-key]
  (try
    (let [n (:n jwks-key)  ; modulus
          e (:e jwks-key)  ; exponent
          ]
      ;; Создаем RSA публичный ключ из модуля и экспоненты
      (let [spec (java.security.spec.RSAPublicKeySpec.
                   (from-base64url-uint n)
                   (from-base64url-uint e))
            key-factory (java.security.KeyFactory/getInstance "RSA")]
        (.generatePublic key-factory spec)))
    (catch Exception e
      (throw e))))

(defn- get-signing-key-from-jwt
  "Получает подписывающий ключ из JWKS на основе JWT токена (аналогично Python jwt.JWKClient.get_signing_key_from_jwt)"
  [token jwks-uri]
  (try
    (let [jwks (fetch-jwks jwks-uri)
          keys (:keys jwks)
          header (jwt-header token)
          kid (:kid header)]

      (if kid
        (let [signing-key (first (filter #(= (:kid %) kid) keys))]
          (if signing-key
            (do
              (let [public-key (jwks-key->public-key signing-key)]
                {:valid true
                 :token token
                 :jwks-uri jwks-uri
                 :signing-key public-key
                 :kid kid}))
            (do
              {:valid false
               :error (str "Ключ с kid " kid " не найден в JWKS")
               :token token
               :jwks-uri jwks-uri
               :kid kid})))
        (do
          {:valid false
           :error "JWT заголовок не содержит kid"
           :token token
           :jwks-uri jwks-uri})))
    (catch Exception e
      {:valid false
       :error (.getMessage e)
       :token token
       :jwks-uri jwks-uri})))

(defn validate-jwt-token
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

          (let [decoded-token (jwt/unsign token signing-key {:alg alg-key})]
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

(defn- create-new-twork-auth-user!
  "Создает нового пользователя через TWork авторизацию. Аккаунт считается активным сразу."
  [new-user]
  (user/create-new-twork-auth-user! new-user))

(defn- fetch-or-create-twork-user!
  "Получает или создает пользователя на основе информации из TWork токена."
  [user-info]
  (let [{:keys [email first-name last-name]} user-info]
    (or (db/select-one [User :id :email :last_login :is_active] :%lower.email (u/lower-case-en email))
        (-> (create-new-twork-auth-user! {:first_name first-name
                                          :last_name  last-name
                                          :email      email})
            (assoc :is_active true)))))

(defn twork-login
  "Выполняет вход через TWork токен и возвращает новую сессию."
  [decoded-token device-info]
  (try
    (let [email (get-in decoded-token [:email])
          first-name (or (get-in decoded-token [:given_name])
                         (get-in decoded-token [:name])
                         "TWork")
          last-name (or (get-in decoded-token [:family_name])
                        "User")]

      (when-not email
        (throw (ex-info "TWork токен не содержит email"
                        {:status-code 400
                         :errors      {:token "TWork токен не содержит email"}})))

      (let [user (fetch-or-create-twork-user! {:email email
                                               :first-name first-name
                                               :last-name last-name})]
        (if (:is_active user)
          (api.session/create-session! :sso user device-info)
          (throw (ex-info "Ваш аккаунт отключен. Обратитесь к администратору."
                          {:status-code 401
                           :errors      {:_error "Ваш аккаунт отключен."}})))))
    (catch Exception e
      (log/error e "Ошибка при создании сессии TWork")
      (throw e))))

(defn- wrap-twork-token* [{:keys [headers], :as request}]
  (if-let [auth-header (get headers "authorization")]
    (let [token (when (str/starts-with? auth-header "Bearer ")
                  (subs auth-header 7))]
      (if token
        (do
          ;; Получаем конфигурацию TWork и валидируем токен
          (try
            (let [discovery-config (twork/fetch-twork-discovery-config)
                  jwks-uri (:jwks_uri discovery-config)]
              (when jwks-uri
                (let [validation-result (validate-jwt-token token jwks-uri)]
                  (if (:valid validation-result)
                    (let [decoded-token (:decoded-token validation-result)
                          device-info (request.u/device-info request)
                          session (twork-login decoded-token device-info)
                          request-time (t/zoned-date-time (t/zone-id "GMT"))]
                      ;; Создаем ответ с cookie сессии
                      (let [response {:id (str (:id session))}]
                        (-> request
                            (assoc :twork-token token)
                            (assoc :metabase-session-id (str (:id session)))
                            (assoc :metabase-session-type :normal)
                            (assoc :metabase-user-id (:user_id session))
                            (assoc :twork-session-response
                                   (mw.session/set-session-cookies request response session request-time)))))
                    (do
                      (log/error "JWT токен невалиден:" (:error validation-result))
                      (assoc request :twork-token token))))))
            (catch Exception e
              (log/error e "Ошибка при получении TWork конфигурации для валидации токена")
              (assoc request :twork-token token)))

          (assoc request :twork-token token))
        request))
    request))

(defn wrap-twork-token
  "Middleware that извлекает TWork токен из заголовка Authorization, валидирует его и создает сессию.
  Токен должен быть в формате 'Bearer <token>'."
  [handler]
  (fn [request respond raise]
    (let [request-with-token (wrap-twork-token* request)]
      (if (:twork-session-response request-with-token)
        ;; Если у нас есть ответ с сессией, возвращаем его напрямую
        (respond (:twork-session-response request-with-token))
        ;; Иначе продолжаем обработку запроса
        (handler request-with-token respond raise)))))
