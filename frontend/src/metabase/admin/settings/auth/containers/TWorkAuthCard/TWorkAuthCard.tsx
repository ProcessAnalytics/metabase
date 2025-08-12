import { t } from "ttag";
import { connect } from "react-redux";
import { getSetting } from "metabase/selectors/settings";
import { updateSettings } from "metabase/admin/settings/settings";
import { Dispatch, State } from "metabase-types/store";
import AuthCard, { AuthCardProps } from "../../components/AuthCard";
import { TWORK_SCHEMA } from "../../constants";

type StateProps = Omit<AuthCardProps, "setting" | "onChange" | "onDeactivate">;
type DispatchProps = Pick<AuthCardProps, "onDeactivate">;

const mapStateToProps = (state: State): StateProps => ({
  type: "twork",
  name: t`TWork`,
  description: t`Allow users to login via TWork.`,
  isConfigured: getSetting(state, "twork-auth-configured?"),
});

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
  onDeactivate: () => dispatch(updateSettings(TWORK_SCHEMA.getDefault())),
});

export default connect(mapStateToProps, mapDispatchToProps)(AuthCard);
