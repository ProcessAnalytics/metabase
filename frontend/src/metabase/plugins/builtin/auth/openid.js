import { t } from "ttag";
import { updateIn } from "icepick";

import {
  PLUGIN_ADMIN_SETTINGS_UPDATES,
  PLUGIN_IS_PASSWORD_USER,
} from "metabase/plugins";

import SettingsOpenidForm from "metabase/admin/settings/components/SettingsOpenidForm";
import OpenidAuthCard from "metabase/admin/settings/auth/containers/OpenIDAuthCard";

PLUGIN_ADMIN_SETTINGS_UPDATES.push(
  sections =>
    updateIn(sections, ["authentication", "settings"], settings => [
      ...settings,
      {
        key: "openid-auth-enabled",
        description: null,
        noHeader: true,
        widget: OpenidAuthCard,
      },
    ]),
  sections => ({
    ...sections,
    "authentication/openid": {
      component: SettingsOpenidForm,
      settings: [
        {
          key: "openid-auth-enabled",
          display_name: t`OpenID Connect Authentication`,
          description: null,
          type: "boolean",
          getHidden: () => true,
        },
        {
          key: "openid-auth-config-url",
          display_name: t`Configuration URL`,
          placeholder: "https://your-provider.com/.well-known/openid_configuration",
          type: "string",
          required: true,
          autoFocus: true,
        },
        {
          key: "openid-auth-issuer",
          display_name: t`Issuer`,
          placeholder: "https://your-provider.com",
          type: "string",
          required: true,
        },
        {
          key: "openid-auth-client-id",
          display_name: t`Client ID`,
          type: "string",
          required: true,
        },
        {
          key: "openid-auth-redirect-uri",
          display_name: t`Redirect URI`,
          placeholder: "https://your-metabase.com/auth/openid/callback",
          type: "string",
          required: true,
        },
        {
          key: "openid-auth-response-type",
          display_name: t`Response Type`,
          type: "select",
          options: [
            { value: "code", name: "code" },
          ],
          defaultValue: "code",
          required: false,
        },
        {
          key: "openid-auth-scope",
          display_name: t`Scope`,
          type: "string",
          defaultValue: "openid profile offline_access",
          required: false,
        },
        {
          key: "openid-auth-grant-type",
          display_name: t`Grant Type`,
          type: "select",
          options: [
            { value: "authorization_code", name: "authorization_code" },
          ],
          defaultValue: "authorization_code",
          required: false,
        },
      ],
    },
  }),
);

PLUGIN_IS_PASSWORD_USER.push(user => !user.openid_auth);
