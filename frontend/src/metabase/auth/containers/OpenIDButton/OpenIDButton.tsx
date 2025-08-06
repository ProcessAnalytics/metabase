import { connect } from "react-redux";
import { getSetting } from "metabase/selectors/settings";
import type { State } from "metabase-types/store";
import OpenIDButton from "../../components/OpenIDButton/OpenIDButton";
import { loginOpenID } from "../../actions";

const mapStateToProps = (state: State) => ({
  isEnabled: getSetting(state, "openid-auth-enabled"),
});

const mapDispatchToProps = {
  onLogin: loginOpenID,
};

export default connect(mapStateToProps, mapDispatchToProps)(OpenIDButton);
