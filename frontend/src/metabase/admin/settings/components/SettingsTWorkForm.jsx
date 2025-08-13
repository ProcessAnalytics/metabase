import React, { useCallback } from "react";
import PropTypes from "prop-types";
import { t } from "ttag";
import { connect } from "react-redux";
import { updateTWorkSettings} from "metabase/admin/settings/settings";
import SettingsBatchForm from "./SettingsBatchForm";
import { FormButton } from "./SettingsTWorkForm.styled";

const propTypes = {
  settingValues: PropTypes.object.isRequired,
  onSubmit: PropTypes.func.isRequired,
};

const SettingsTWorkForm = ({ settingValues, onSubmit, ...props }) => {
  const isEnabled = settingValues["twork-auth-enabled"];
  const layout = getLayout(settingValues);
  const breadcrumbs = getBreadcrumbs();

  const handleSubmit = useCallback(
    values => {
      return onSubmit({ ...values, "twork-auth-enabled": true });
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

SettingsTWorkForm.propTypes = propTypes;

const getLayout = settingValues => {
  return [
    {
      title: t`Provider Configuration`,
      settings: [
        "twork-auth-config-url",
        "twork-auth-issuer",
        "twork-auth-client-id",
      ],
    },
    {
      title: t`Authentication Settings`,
      settings: [
        "twork-auth-redirect-uri",
        "twork-auth-response-type",
        "twork-auth-scope",
        "twork-auth-grant-type",
      ],
    },
    {
      title: t`PeopleHub`,
      settings: [
        "twork-auth-people-hub-client-secret",
        "twork-auth-people-hub-client-id",
        "twork-auth-people-hub-auth-token-host",
        "twork-auth-people-hub-scope",
        "twork-auth-people-hub-employee-reader-host",
      ],
    },
  ];
};

const getBreadcrumbs = () => {
  return [[t`Authentication`, "/admin/settings/authentication"], [t`TWork Connect`]];
};

const mapDispatchToProps = {
  onSubmit: updateTWorkSettings,
};

export default connect(null, mapDispatchToProps)(SettingsTWorkForm);
