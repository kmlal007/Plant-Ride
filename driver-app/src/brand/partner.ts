import { ImageSourcePropType } from 'react-native';

/** Co-brand partner shown as "by <partner>". See brand/partner/README.md in the repository. */
export const PARTNER_NAME = 'SRM Eco Tech';

/**
 * Set to require('../../assets/partner-logo.png') once the official SRM Eco Tech artwork (light version for dark
 * backgrounds) is copied into assets/. Until then the name is shown as text.
 */
export const PARTNER_LOGO: ImageSourcePropType | null = null;
