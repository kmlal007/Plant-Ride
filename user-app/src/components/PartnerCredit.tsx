import { Image, Text, View } from 'react-native';
import { PARTNER_LOGO, PARTNER_NAME } from '../brand/partner';

/** "by SRM Eco Tech" credit for dark (navy) backgrounds: login and loading screens. */
export function PartnerCredit() {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }} accessibilityLabel={`by ${PARTNER_NAME}`}>
      <Text style={{ color: '#9FB3C8', fontSize: 11, letterSpacing: 1.6, textTransform: 'uppercase' }}>by</Text>
      {PARTNER_LOGO ? (
        <Image source={PARTNER_LOGO} style={{ height: 28, width: 120 }} resizeMode="contain" accessibilityLabel={PARTNER_NAME} />
      ) : (
        <Text style={{ color: '#FFFFFF', fontSize: 14, fontWeight: '800', letterSpacing: 1.2, textTransform: 'uppercase' }}>
          {PARTNER_NAME}
        </Text>
      )}
    </View>
  );
}
