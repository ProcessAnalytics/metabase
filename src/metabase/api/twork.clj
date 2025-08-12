(ns metabase.api.twork
  "/api/twork endpoints"
  (:require
   [compojure.core :refer [PUT POST GET]]
   [java-time :as t]
   [metabase.api.common :as api]
   [metabase.integrations.twork :as twork]
   [metabase.models.setting :as setting]
   [metabase.util.schema :as su]
   [metabase.util.log :as log]
   [schema.core :as s]
   [toucan.db :as db]))

(set! *warn-on-reflection* true)

#_{:clj-kondo/ignore [:deprecated-var]}
(api/defendpoint-schema PUT "/settings"
  "Update TWork Connect related settings. You must be a superuser or have `setting` permission to do this."
  [:as {settings :body}]
  {settings su/Map}
  (api/check-superuser)
  (let [twork-settings (-> settings
                            (select-keys [:twork-auth-config-url
                                         :twork-auth-issuer
                                         :twork-auth-client-id
                                         :twork-auth-redirect-uri
                                         :twork-auth-response-type
                                         :twork-auth-scope
                                         :twork-auth-grant-type
                                         :twork-auth-people-hub-client-secret
                                         :twork-auth-people-hub-client-id
                                         :twork-auth-people-hub-auth-token-host
                                         :twork-auth-people-hub-scope
                                         :twork-auth-people-hub-employee-reader-host])
                            )
        test-settings   {:config-url (:twork-auth-config-url twork-settings)
                         :issuer     (:twork-auth-issuer twork-settings)
                         :client-id  (:twork-auth-client-id twork-settings)}]
    (db/transaction
         (setting/set-many! twork-settings)
         (setting/set-value-of-type! :boolean :twork-auth-enabled (boolean (:twork-auth-enabled settings))))))

#_{:clj-kondo/ignore [:deprecated-var]}
(api/defendpoint-schema GET "/auth_url"
  []
  (let [result (twork/fetch-auth-url)]
    {:authorization_url (:authorization_url result)}))

#_{:clj-kondo/ignore [:deprecated-var]}
(api/defendpoint-schema GET "/callback"
  [:as {{:keys [code]} :params, :as request}]
  {code (s/maybe su/NonBlankString)}

  (when-not code
    (throw (ex-info "Authorization code is required" {:status-code 400})))

  (let [access-token (twork/fetch-access-token code)]
    ;; Возвращаем токен для фронтенда, который затем вызовет /twork_auth
    {:token access-token}))

;#_{:clj-kondo/ignore [:deprecated-var]}
;(api/defendpoint-schema POST "/session"
;  "Обменяет TWork токен на сессию Metabase."
;  [:as {{:keys [token]} :body, :as request}]
;  {token su/NonBlankString}
;
;  (when-not token
;    (throw (ex-info "TWork токен обязателен" {:status-code 400})))
;
;  (try
;    (let [discovery-config (twork/fetch-twork-discovery-config)
;          jwks-uri (:jwks_uri discovery-config)]
;      (when jwks-uri
;        (let [validation-result (validate-jwt-token token jwks-uri)]
;          (if (:valid validation-result)
;            (let [decoded-token (:decoded-token validation-result)
;                  device-info (request.u/device-info request)
;                  session (twork-login decoded-token device-info)
;                  request-time (t/zoned-date-time (t/zone-id "GMT"))
;                  response {:id (str (:id session))}]
;              (mw.session/set-session-cookies request response session request-time))
;            (throw (ex-info "Невалидный TWork токен"
;                           {:status-code 401
;                            :error (:error validation-result)}))))))
;    (catch Exception e
;      (log/error e "Ошибка при обмене TWork токена на сессию")
;      (throw (ex-info "Ошибка аутентификации TWork"
;                     {:status-code 500
;                      :error (.getMessage e)})))))

(api/define-routes)
