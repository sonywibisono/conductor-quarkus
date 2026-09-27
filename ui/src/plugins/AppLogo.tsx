import { OpenedLogo } from "components/providers/sidebar/OpenedLogo";
import { FEATURES, featureFlags } from "utils";

const customLogo = featureFlags.getValue(FEATURES.CUSTOM_LOGO_URL);

export default function AppLogo() {
  return customLogo ? (
    <OpenedLogo customLogo={customLogo} width="60%" pl={6} />
  ) : (
    <img
      src="/logo-pln.png"
      alt="PLN"
      style={{
        height: "36px",
        marginRight: 16,
        objectFit: "contain",
      }}
    />
  );
}
