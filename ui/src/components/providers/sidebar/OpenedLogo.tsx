import Stack, { StackProps } from "@mui/material/Stack";

// Logo PLN digunakan untuk semua mode (OSS maupun enterprise)
const PLN_LOGO = "/logo-pln.png";
const PLN_ALT = "PLN logo";

export const OpenedLogo = ({
  customLogo,
  ...rest
}: StackProps & {
  customLogo?: string;
}) => (
  <Stack
    {...rest}
    flexDirection="row"
    alignItems="center"
    justifyContent="center"
    height="100%"
    sx={{ transition: "all 0.2s ease-in-out" }}
  >
    <img
      src={customLogo || PLN_LOGO}
      alt={customLogo ? "Custom logo" : PLN_ALT}
      style={{
        transition: "all 0.2s ease-in-out",
        height: "100%",
        maxWidth: "80%",
        objectFit: "contain",
      }}
    />
  </Stack>
);
