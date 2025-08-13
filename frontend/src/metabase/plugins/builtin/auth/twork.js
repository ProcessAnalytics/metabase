import { t } from "ttag";
import { updateIn } from "icepick";

import {
  PLUGIN_AUTH_PROVIDERS,
  PLUGIN_ADMIN_SETTINGS_UPDATES,
  PLUGIN_IS_PASSWORD_USER,
} from "metabase/plugins";


import SettingsTWorkForm from "metabase/admin/settings/components/SettingsTWorkForm";
import TWorkAuthCard from "metabase/admin/settings/auth/containers/TWorkAuthCard";

PLUGIN_AUTH_PROVIDERS.push(providers => {
  const tworkProvider = {
    name: "twork",
    // circular dependencies
    Button: require("metabase/auth/containers/TWorkButton").default,
  };

  // Always register the provider, but let the component handle the enabled state
  return [tworkProvider, ...providers];
});

PLUGIN_ADMIN_SETTINGS_UPDATES.push(
  sections =>
    updateIn(sections, ["authentication", "settings"], settings => [
      ...settings,
      {
        key: "twork-auth-enabled",
        description: null,
        noHeader: true,
        widget: TWorkAuthCard,
      },
    ]),
  sections => ({
    ...sections,
    "authentication/twork": {
      component: SettingsTWorkForm,
      settings: [
        {
          key: "twork-auth-enabled",
          display_name: t`TWork Connect Authentication`,
          description: null,
          type: "boolean",
          getHidden: () => true,
        },
        {
          key: "twork-auth-config-url",
          display_name: t`Configuration URL`,
          type: "string",
          required: true,
          autoFocus: true,
        },
        {
          key: "twork-auth-issuer",
          display_name: t`Issuer`,
          type: "string",
          required: true,
        },
        {
          key: "twork-auth-client-id",
          display_name: t`Client ID`,
          type: "string",
          required: true,
        },
        {
          key: "twork-auth-redirect-uri",
          display_name: t`Redirect URI`,
          type: "string",
          required: true,
        },
        {
          key: "twork-auth-response-type",
          display_name: t`Response Type`,
          type: "select",
          options: [
            { value: "code", name: "code" },
          ],
          defaultValue: "code",
          required: false,
        },
        {
          key: "twork-auth-scope",
          display_name: t`Scope`,
          type: "string",
          defaultValue: "openid profile offline_access",
          required: false,
        },
        {
          key: "twork-auth-grant-type",
          display_name: t`Grant Type`,
          type: "select",
          options: [
            { value: "authorization_code", name: "authorization_code" },
          ],
          defaultValue: "authorization_code",
          required: false,
        },
        {
          key: "twork-auth-people-hub-client-secret",
          display_name: t`PeopleHub Client Secret`,
          type: "password",
          required: true,
        },
        {
          key: "twork-auth-people-hub-client-id",
          display_name: t`PeopleHub Client ID`,
          type: "string",
          required: true,
        },
        {
          key: "twork-auth-people-hub-auth-token-host",
          display_name: t`PeopleHub Auth Token Host`,
          type: "string",
          required: true,
        },
        {
          key: "twork-auth-people-hub-scope",
          display_name: t`PeopleHub Scope`,
          type: "string",
          defaultValue: "hrp_public_api hrp_employee_reader_public",
          required: false,
        },
        {
          key: "twork-auth-people-hub-employee-reader-host",
          display_name: t`PeopleHub EmployeeReader Host`,
          type: "string",
          required: true,
        },
      ],
    },
  }),
);

PLUGIN_IS_PASSWORD_USER.push(_ => false);
