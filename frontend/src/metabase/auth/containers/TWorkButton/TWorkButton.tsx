import { connect } from "react-redux";
import { getSetting } from "metabase/selectors/settings";
import type { State } from "metabase-types/store";
import TWorkButton from "metabase/auth/components/TWorkButton/TWorkButton";
import { loginTWork } from "../../actions";

const mapStateToProps = (state: State) => ({
  isEnabled: getSetting(state, "twork-auth-enabled"),
});

const mapDispatchToProps = {
  onLogin: loginTWork,
};

export default connect(mapStateToProps, mapDispatchToProps)(TWorkButton);
