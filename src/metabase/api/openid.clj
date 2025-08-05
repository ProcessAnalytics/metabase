(ns metabase.api.openid
  "/api/openid endpoints"
  (:require
   [compojure.core :refer [PUT]]
   [metabase.api.common :as api]
   [metabase.util.schema :as su]
   [toucan.db :as db]))

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

(api/define-routes)
