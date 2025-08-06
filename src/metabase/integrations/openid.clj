(ns metabase.integrations.openid
    "OpenID Connect integration for Metabase"
    (:require
      [cheshire.core :as json]
      [clj-http.client :as http]
      [clojure.string :as str]
      [metabase.api.common :as api]
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
      [toucan.db :as db])
    (:import
      (java.net URL)))

(set! *warn-on-reflection* true)

;;; ======================================= Settings =======================================

(defsetting openid-auth-enabled
  (deferred-tru "Is OpenID Connect authentication currently enabled?")
  :type :boolean
  :visibility :public
  :default false)

(defsetting openid-auth-config-url
  (deferred-tru "OpenID Connect configuration URL.")
  :type :string)

(defsetting openid-auth-issuer
  (deferred-tru "OpenID Connect issuer identifier.")
  :type :string)

(defsetting openid-auth-client-id
  (deferred-tru "OpenID Connect client identifier.")
  :type :string)

(defsetting openid-auth-redirect-uri
  (deferred-tru "OpenID Connect redirect URI.")
  :type :string)

(defsetting openid-auth-response-type
  (deferred-tru "OpenID Connect response type.")
  :type :string
  :default "code")

(defsetting openid-auth-scope
  (deferred-tru "OpenID Connect scope.")
  :type :string
  :default "openid profile offline_access")

(defsetting openid-auth-grant-type
  (deferred-tru "OpenID Connect grant type.")
  :type :string
  :default "authorization_code")

(defsetting openid-auth-configured
  (deferred-tru "Is OpenID Connect configured?")
  :type :boolean
  :visibility :public
  :setter :none
  :getter
  (fn []
    (boolean
     (and (openid-auth-client-id)
          (openid-auth-issuer)
          (openid-auth-redirect-uri)
          (openid-auth-config-url)))))

;;; ======================================= Core Functions =======================================

(defn openid-enabled?
  "Is OpenID Connect authentication currently enabled?"
  []
  (openid-auth-enabled))

(defn fetch-openid-configuration
  "Fetch OpenID Connect configuration from the discovery endpoint."
  [config-url]
  (try
    (log/info "Fetching OpenID configuration from:" config-url)
    (let [response (http/get config-url
                             {:throw-exceptions false
                              :insecure?        true
                              ; Allow insecure SSL for testing
                              :accept           :json})]
      (log/info "OpenID configuration response status:" (:status response))
      (if (= 200 (:status response))
        (json/parse-string (:body response) true)
        (do
          (log/error "OpenID configuration failed with status:" (:status response) "body:" (:body response))
          (throw
            (ex-info (tru "Failed to fetch OpenID configuration")
                     {:status (:status response)
                      :body   (:body response)})))))
    (catch Exception e
      (log/error e "Exception while fetching OpenID configuration from:" config-url)
      (throw
        (ex-info (tru "Error fetching OpenID configuration: {0}" (.getMessage e))
                 {:error e})))))

(defn build-authorization-url
  "Build the OpenID Connect authorization URL with required parameters."
  [authorization-endpoint client-id redirect-uri response-type scope]
  (let [params {:client_id     client-id
                :redirect_uri  redirect-uri
                :response_type response-type
                :scope         scope}]
    (str authorization-endpoint "?" (http/generate-query-string params))))

(defn fetch-auth-url
  "Initiate OpenID Connect authentication flow."
  []
  (when-not (openid-enabled?)
            (throw
              (ex-info (tru "OpenID Connect is not enabled")
                       {:status-code 400})))

  (let [config-url    (openid-auth-config-url)
        client-id     (openid-auth-client-id)
        redirect-uri  (openid-auth-redirect-uri)
        response-type (openid-auth-response-type)
        scope         (openid-auth-scope)]

    (when-not (and config-url client-id redirect-uri)
              (throw
                (ex-info (tru "OpenID Connect is not properly configured")
                         {:status-code 400})))

    (try
      (let [config                 (fetch-openid-configuration config-url)
            authorization-endpoint (:authorization_endpoint config)]

        (when-not authorization-endpoint
                  (throw
                    (ex-info (tru "Authorization endpoint not found in OpenID configuration")
                             {:status-code 400})))

        (let [authorization-url (build-authorization-url
                                 authorization-endpoint
                                 client-id
                                 redirect-uri
                                 response-type
                                 scope)]

          {:authorization_url authorization-url}))
      (catch Exception e
        (log/error e "Error initiating OpenID authentication")
        (throw
          (ex-info (tru "Failed to initiate OpenID authentication: {0}" (.getMessage e))
                   {:status-code 500
                    :error       e}))))))

(defn validate-openid-settings
  "Validate OpenID Connect settings by testing the configuration URL."
  [config-url issuer client-id]
  (try
    (let [config (fetch-openid-configuration config-url)]
      (log/info "OpenID configuration fetched successfully:" (keys config))
      (cond
       (not= issuer (:issuer config))
       {:status  :ERROR
        :message (tru "Issuer mismatch. Expected: {0}, Got: {1}" issuer (:issuer config))}

       (not (:authorization_endpoint config))
       {:status  :ERROR
        :message (tru "Missing authorization endpoint in OpenID configuration")}

       (not (:token_endpoint config))
       {:status  :ERROR
        :message (tru "Missing token endpoint in OpenID configuration")}

       :else
       {:status :SUCCESS}))
    (catch Exception e
      {:status  :ERROR
       :message (.getMessage e)})))

(defn openid-settings
  "A map of all OpenID Connect settings"
  []
  {:config-url    (openid-auth-config-url)
   :issuer        (openid-auth-issuer)
   :client-id     (openid-auth-client-id)
   :redirect-uri  (openid-auth-redirect-uri)
   :response-type (openid-auth-response-type)
   :scope         (openid-auth-scope)
   :grant-type    (openid-auth-grant-type)})

(defn fetch-openid-discovery-config
  "Получает конфигурацию OpenID Connect из discovery endpoint и возвращает ключевые параметры.
   Возвращает словарь с ключами: :issuer, :id_token_signing_alg_values_supported, :jwks_uri"
  []
  (try
    (let [config-url (openid-auth-config-url)]
      (when-not config-url
        (throw (ex-info (tru "OpenID config URL не настроен") {:status-code 400})))

      (log/info "Получение OpenID discovery конфигурации из:" config-url)
      (let [config (fetch-openid-configuration config-url)]
        {:issuer                              (:issuer config)
         :id_token_signing_alg_values_supported (:id_token_signing_alg_values_supported config)
         :jwks_uri                            (:jwks_uri config)}))
    (catch Exception e
      (log/error e "Ошибка при получении OpenID discovery конфигурации")
      (throw (ex-info (tru "Не удалось получить OpenID discovery конфигурацию: {0}" (.getMessage e))
                      {:status-code 500
                       :error       e})))))

;;; ======================================= User Management =======================================


(defn fetch-access-token
  [code]
  (when-not (openid-enabled?)
            (throw
              (ex-info (tru "OpenID Connect is not enabled")
                       {:status-code 400})))

  (when-not code
            (throw
              (ex-info (tru "Authorization code is required")
                       {:status-code 400})))

  (try
    (let [config-url     (openid-auth-config-url)
          client-id      (openid-auth-client-id)
          grant-type     (openid-auth-grant-type)
          redirect-uri   (openid-auth-redirect-uri)
          config         (fetch-openid-configuration config-url)
          token-endpoint (:token_endpoint config)]

      (when-not token-endpoint
                (throw
                  (ex-info (tru "Token endpoint not found in OpenID configuration")
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
      (log/error e "Error during OpenID authentication")
      (throw
        (ex-info (tru "OpenID authentication failed: {0}" (.getMessage e))
                 {:status-code 500
                  :error       e})))))
