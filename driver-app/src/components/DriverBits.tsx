import { Ionicons } from '@expo/vector-icons';
import { Text, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import { useTheme } from '../theme';

/** Indian commercial vehicles carry yellow plates; mirroring that makes the vehicle instantly recognisable. */
export function NumberPlate({ value }: { value: string }) {
  return (
    <View
      style={{
        backgroundColor: '#FFD400',
        borderColor: '#111111',
        borderWidth: 2,
        borderRadius: 6,
        paddingHorizontal: 10,
        paddingVertical: 4,
        alignSelf: 'flex-start',
      }}
    >
      <Text style={{ color: '#111111', fontWeight: '900', fontSize: 18, letterSpacing: 1.5 }}>{value}</Text>
    </View>
  );
}

/** Seconds left to accept an offer, as a ring that empties. */
export function CountdownRing({ seconds, total }: { seconds: number; total: number }) {
  const t = useTheme();
  const size = 76;
  const stroke = 7;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const frac = Math.max(0, Math.min(1, seconds / Math.max(1, total)));
  const colour = seconds <= 10 ? t.danger : t.accent;
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} style={{ position: 'absolute' }}>
        <Circle cx={size / 2} cy={size / 2} r={r} stroke={t.border} strokeWidth={stroke} fill="none" />
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={colour}
          strokeWidth={stroke}
          fill="none"
          strokeDasharray={`${c} ${c}`}
          strokeDashoffset={c * (1 - frac)}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </Svg>
      <Text style={{ fontSize: 22, fontWeight: '900', color: colour }}>{seconds}</Text>
      <Text style={{ fontSize: 10, color: t.muted, marginTop: -2 }}>sec</Text>
    </View>
  );
}

/** "Waiting for rides" visual: a car in expanding rings. */
export function RadarIllustration() {
  const t = useTheme();
  return (
    <View style={{ alignItems: 'center', justifyContent: 'center', height: 150 }}>
      {[140, 104, 68].map((d, i) => (
        <View
          key={d}
          style={{
            position: 'absolute',
            width: d,
            height: d,
            borderRadius: d / 2,
            borderWidth: 2,
            borderColor: t.accent,
            opacity: 0.15 + i * 0.2,
          }}
        />
      ))}
      <View style={{ width: 52, height: 52, borderRadius: 26, backgroundColor: t.accent, alignItems: 'center', justifyContent: 'center' }}>
        <Ionicons name="car-sport" size={28} color={t.onAccent} />
      </View>
    </View>
  );
}

/** "Step 2 of 3 · Waiting for rider" header for the active job. */
export function StageHeader({ step, total, title, icon }: { step: number; total: number; title: string; icon: React.ComponentProps<typeof Ionicons>['name'] }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
      <View style={{ width: 40, height: 40, borderRadius: 12, backgroundColor: t.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
        <Ionicons name={icon} size={22} color={t.accent} />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 12, color: t.muted, fontWeight: '700' }}>
          STEP {step} OF {total}
        </Text>
        <Text style={{ fontSize: 19, fontWeight: '800', color: t.text }}>{title}</Text>
      </View>
    </View>
  );
}
