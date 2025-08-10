import * as Yup from "yup";
import * as Errors from "metabase/core/utils/errors";
import { SettingDefinition } from "metabase-types/api";

const REQUIRED_SCHEMA = {
  is: (isEnabled: boolean, setting?: SettingDefinition) =>
    isEnabled && !setting?.is_env_setting,
  then: (schema: Yup.AnySchema) => schema.required(Errors.required),
};

export const GOOGLE_SCHEMA = Yup.object({
  "google-auth-enabled": Yup.boolean().nullable().default(false),
  "google-auth-client-id": Yup.string()
    .nullable()
    .default(null)
    .when(["google-auth-enabled", "$google-auth-client-id"], REQUIRED_SCHEMA),
  "google-auth-auto-create-accounts-domain": Yup.string()
    .nullable()
    .default(null),
});

export const TWORK_SCHEMA = Yup.object({
  "twork-auth-enabled": Yup.boolean().nullable().default(false),
  "twork-auth-config-url": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-config-url"], REQUIRED_SCHEMA),
  "twork-auth-issuer": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-issuer"], REQUIRED_SCHEMA),
  "twork-auth-client-id": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-client-id"], REQUIRED_SCHEMA),
  "twork-auth-redirect-uri": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-redirect-uri"], REQUIRED_SCHEMA),
  "twork-auth-response-type": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-response-type"], REQUIRED_SCHEMA),
  "twork-auth-scope": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-scope"], REQUIRED_SCHEMA),
  "twork-auth-grant-type": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-grant-type"], REQUIRED_SCHEMA),
  "twork-auth-people-hub-client-secret": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-people-hub-client-secret"], REQUIRED_SCHEMA),
  "twork-auth-people-hub-client-id": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-people-hub-client-id"], REQUIRED_SCHEMA),
  "twork-auth-people-hub-auth-token-host": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-people-hub-auth-token-host"], REQUIRED_SCHEMA),
  "twork-auth-people-hub-scope": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-people-hub-scope"], REQUIRED_SCHEMA),
  "twork-auth-people-hub-employee-reader-host": Yup.string().nullable().default(null).when(["twork-auth-enabled", "$twork-auth-people-hub-employee-reader-host"], REQUIRED_SCHEMA),

});

export const LDAP_SCHEMA = Yup.object({
  "ldap-enabled": Yup.boolean().nullable().default(false),
  "ldap-host": Yup.string().nullable().default(null),
  "ldap-port": Yup.number().nullable().default(null),
  "ldap-security": Yup.string().nullable().default("none"),
  "ldap-bind-dn": Yup.string().nullable().default(null),
  "ldap-password": Yup.string().nullable().default(null),
  "ldap-user-base": Yup.string().nullable().default(null),
  "ldap-user-filter": Yup.string().nullable().default(null),
  "ldap-attribute-email": Yup.string().nullable().default(null),
  "ldap-attribute-firstname": Yup.string().nullable().default(null),
  "ldap-attribute-lastname": Yup.string().nullable().default(null),
  "ldap-group-sync": Yup.boolean().nullable().default(false),
  "ldap-group-base": Yup.string().nullable().default(null),
  "ldap-group-mappings": Yup.object().nullable().default(null),
});
