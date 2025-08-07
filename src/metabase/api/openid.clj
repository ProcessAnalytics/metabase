(ns metabase.api.openid
  "/api/openid endpoints"
  (:require
   [compojure.core :refer [PUT POST]]
   [java-time :as t]
   [metabase.api.common :as api]
   [metabase.api.session :as api.session]
   [metabase.integrations.openid :as openid]
   [metabase.server.middleware.session :as mw.session]
   [metabase.models.session :as session]
   [metabase.models.setting :as setting]
   [metabase.util.schema :as su]
   [schema.core :as s]
   [toucan.db :as db]
   [metabase.server.request.util :as request.u]))

(set! *warn-on-reflection* true)

#_{:clj-kondo/ignore [:deprecated-var]}
(api/defendpoint-schema PUT "/settings"
  "Update OpenID Connect related settings. You must be a superuser or have `setting` permission to do this."
  [:as {settings :body}]
  {settings su/Map}
  (api/check-superuser)
  (let [openid-settings (-> settings
                            (select-keys [:openid-auth-config-url
                                         :openid-auth-issuer
                                         :openid-auth-client-id
                                         :openid-auth-redirect-uri
                                         :openid-auth-response-type
                                         :openid-auth-scope
                                         :openid-auth-grant-type])
                            )
        test-settings   {:config-url (:openid-auth-config-url openid-settings)
                         :issuer     (:openid-auth-issuer openid-settings)
                         :client-id  (:openid-auth-client-id openid-settings)}]
    (db/transaction
         (setting/set-many! openid-settings)
         (setting/set-value-of-type! :boolean :openid-auth-enabled (boolean (:openid-auth-enabled settings))))))

#_{:clj-kondo/ignore [:deprecated-var]}
(api/defendpoint-schema GET "/auth_url"
  []
  (let [result (openid/fetch-auth-url)]
    {:authorization_url (:authorization_url result)}))

#_{:clj-kondo/ignore [:deprecated-var]}
(api/defendpoint-schema GET "/callback"
  [:as {{:keys [code]} :params, :as request}]
  {code (s/maybe su/NonBlankString)}

  (when-not code
    (throw (ex-info "Authorization code is required" {:status-code 400})))

  (let [access_token (openid/fetch-access-token code)]
    {:access_token access_token}))

(api/define-routes)
