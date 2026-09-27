// Logo PLN digunakan untuk semua mode (OSS maupun enterprise)
const PLN_LOGO = "/logo-pln.png";

export const ClosedLogo = ({ customLogo }: { customLogo?: string }) => (
  <img
    src={customLogo || PLN_LOGO}
    alt="PLN logo"
    style={{
      width: "32px",
      height: "32px",
      objectFit: "contain",
    }}
  />
);
