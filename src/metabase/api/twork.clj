(ns metabase.api.twork
  "/api/twork endpoints"
  (:require
   [compojure.core :refer [PUT]]
   [metabase.api.common :as api]
   [metabase.models.setting :as setting]
   [metabase.util.schema :as su]
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

(api/define-routes)
