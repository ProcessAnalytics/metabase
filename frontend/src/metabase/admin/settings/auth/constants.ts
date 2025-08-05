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

export const OPENID_SCHEMA = Yup.object({
  "openid-auth-enabled": Yup.boolean().nullable().default(false),
  "openid-auth-config-url": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-config-url"], REQUIRED_SCHEMA),
  "openid-auth-issuer": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-issuer"], REQUIRED_SCHEMA),
  "openid-auth-client-id": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-client-id"], REQUIRED_SCHEMA),
  "openid-auth-redirect-uri": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-redirect-uri"], REQUIRED_SCHEMA),
  "openid-auth-response-type": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-response-type"], REQUIRED_SCHEMA),
  "openid-auth-scope": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-scope"], REQUIRED_SCHEMA),
  "openid-auth-grant-type": Yup.string().nullable().default(null).when(["openid-auth-enabled", "$openid-auth-grant-type"], REQUIRED_SCHEMA),
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
