import React, { useCallback } from "react";
import PropTypes from "prop-types";
import { t } from "ttag";
import { connect } from "react-redux";
import { updateOpenidSettings } from "metabase/admin/settings/settings";
import { OPENID_SCHEMA } from "../auth/constants";
import SettingsBatchForm from "./SettingsBatchForm";
import { FormButton } from "./SettingsOpenidForm.styled";

const propTypes = {
  settingValues: PropTypes.object.isRequired,
  onSubmit: PropTypes.func.isRequired,
};

const SettingsOpenidForm = ({ settingValues, onSubmit, ...props }) => {
  const isEnabled = settingValues["openid-auth-enabled"];
  const layout = getLayout(settingValues);
  const breadcrumbs = getBreadcrumbs();

  const handleSubmit = useCallback(
    values => {
      return onSubmit({ ...values, "openid-auth-enabled": true });
    },
    [onSubmit],
  );

  return (
    <SettingsBatchForm
      {...props}
      layout={layout}
      breadcrumbs={breadcrumbs}
      settingValues={settingValues}
      updateSettings={handleSubmit}
      renderSubmitButton={({ disabled, pristine, onSubmit }) => (
        <FormButton
          primary={!disabled}
          disabled={disabled || pristine}
          actionFn={onSubmit}
          normalText={isEnabled ? t`Save changes` : t`Save and enable`}
          successText={t`Success`}
        />
      )}
    />
  );
};

SettingsOpenidForm.propTypes = propTypes;

const getLayout = settingValues => {
  return [
    {
      title: t`Provider Configuration`,
      settings: [
        "openid-auth-config-url",
        "openid-auth-issuer",
        "openid-auth-client-id",
      ],
    },
    {
      title: t`Authentication Settings`,
      settings: [
        "openid-auth-redirect-uri",
        "openid-auth-response-type",
        "openid-auth-scope",
        "openid-auth-grant-type",
      ],
    },
  ];
};

const getBreadcrumbs = () => {
  return [[t`Authentication`, "/admin/settings/authentication"], [t`OpenID Connect`]];
};

const mapDispatchToProps = {
  onSubmit: updateOpenidSettings,
  onDeactivate: () => updateOpenidSettings(OPENID_SCHEMA.getDefault()),
};

export default connect(null, mapDispatchToProps)(SettingsOpenidForm);
