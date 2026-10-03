import Svg, { Circle, Defs, G, LinearGradient, Path, Rect, Stop } from 'react-native-svg';

/** The Plant-Ride mark: a route shaped like a "P" with a stop and a vehicle. */
export function LogoMark({ size = 40, variant = 'rider' }: { size?: number; variant?: 'rider' | 'driver' }) {
  const driver = variant === 'driver';
  return (
    <Svg width={size} height={size} viewBox="0 0 512 512" accessibilityLabel="Plant-Ride">
      <Defs>
        <LinearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor={driver ? '#F7823A' : '#1B4469'} />
          <Stop offset="1" stopColor={driver ? '#C2410C' : '#0B1F33'} />
        </LinearGradient>
        <LinearGradient id="route" x1="0" y1="1" x2="1" y2="0">
          <Stop offset="0" stopColor="#F26B1D" />
          <Stop offset="1" stopColor="#FFA94D" />
        </LinearGradient>
      </Defs>
      <Rect width="512" height="512" rx="116" fill="url(#bg)" />
      <G transform="translate(-14 -24)">
        <Path
          d="M184 396 V164 H292 a86 86 0 0 1 0 172 H236"
          fill="none"
          stroke={driver ? '#0B1F33' : 'url(#route)'}
          strokeWidth={52}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <Circle cx="184" cy="396" r="34" fill="#FFFFFF" />
        <Circle cx="184" cy="396" r="15" fill="#0B1F33" />
        <Circle cx="362" cy="250" r="20" fill="#FFFFFF" />
      </G>
    </Svg>
  );
}
