import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { ComponentProps, ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleProp,
  Text,
  TextInput,
  TextInputProps,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { radius, space, Theme, useTheme } from '../theme';
import { LogoMark } from './Logo';

export type IconName = ComponentProps<typeof Ionicons>['name'];
export type Tone = 'neutral' | 'accent' | 'ok' | 'warn' | 'danger' | 'info' | 'violet';

export function toneColours(t: Theme, tone: Tone): { fg: string; bg: string } {
  switch (tone) {
    case 'accent':
      return { fg: t.accent, bg: t.accentSoft };
    case 'ok':
      return { fg: t.ok, bg: t.okSoft };
    case 'warn':
      return { fg: t.warn, bg: t.warnSoft };
    case 'danger':
      return { fg: t.danger, bg: t.dangerSoft };
    case 'info':
      return { fg: t.info, bg: t.infoSoft };
    case 'violet':
      return { fg: t.violet, bg: t.violetSoft };
    default:
      return { fg: t.text2, bg: t.surface2 };
  }
}

// ------------------------------------------------------------------ layout

/** Screen with a branded gradient header and a scrolling body. */
export function Screen({
  title,
  subtitle,
  right,
  children,
  footer,
  variant = 'rider',
}: {
  title: string;
  subtitle?: string;
  right?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  variant?: 'rider' | 'driver';
}) {
  const t = useTheme();
  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <LinearGradient colors={[t.headerFrom, t.headerTo]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }}>
        <SafeAreaView edges={['top', 'left', 'right']}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingTop: 8, paddingBottom: 18 }}>
            <LogoMark size={36} variant={variant} />
            <View style={{ flex: 1 }}>
              {subtitle ? (
                <Text style={{ color: '#9FB3C8', fontSize: 13 }} numberOfLines={1}>
                  {subtitle}
                </Text>
              ) : null}
              <Text style={{ color: '#FFFFFF', fontSize: 21, fontWeight: '800', letterSpacing: -0.3 }} numberOfLines={1}>
                {title}
              </Text>
            </View>
            {right}
          </View>
        </SafeAreaView>
      </LinearGradient>
      <ScrollView
        style={{ flex: 1, marginTop: -10, borderTopLeftRadius: 18, borderTopRightRadius: 18, backgroundColor: t.bg }}
        contentContainerStyle={{ padding: space.lg, gap: space.md, paddingBottom: 40 }}
      >
        {children}
      </ScrollView>
      {footer ? (
        <SafeAreaView edges={['bottom']} style={{ backgroundColor: t.surface, borderTopWidth: 1, borderTopColor: t.border }}>
          <View style={{ padding: space.md }}>{footer}</View>
        </SafeAreaView>
      ) : null}
    </View>
  );
}

export function HeaderButton({ icon, onPress, label }: { icon: IconName; onPress: () => void; label: string }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      hitSlop={8}
      style={({ pressed }) => ({
        width: 40,
        height: 40,
        borderRadius: 20,
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: pressed ? 'rgba(255,255,255,0.2)' : 'rgba(255,255,255,0.1)',
      })}
    >
      <Ionicons name={icon} size={20} color="#FFFFFF" />
    </Pressable>
  );
}

export function Card({
  children,
  style,
  tone,
}: {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  tone?: Tone;
}) {
  const t = useTheme();
  const accent = tone ? toneColours(t, tone).fg : null;
  return (
    <View
      style={[
        {
          backgroundColor: t.surface,
          borderRadius: radius.lg,
          borderWidth: 1,
          borderColor: t.border,
          padding: space.lg,
          gap: space.sm + 2,
          shadowColor: '#000',
          shadowOpacity: t.dark ? 0 : 0.05,
          shadowRadius: 8,
          shadowOffset: { width: 0, height: 2 },
          elevation: 1,
        },
        accent ? { borderLeftWidth: 4, borderLeftColor: accent } : null,
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function SectionTitle({ icon, children, right }: { icon?: IconName; children: ReactNode; right?: ReactNode }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      {icon ? <Ionicons name={icon} size={18} color={t.accent} /> : null}
      <Text style={{ flex: 1, fontSize: 16, fontWeight: '700', color: t.text }}>{children}</Text>
      {right}
    </View>
  );
}

export function Row({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center', gap: space.sm }, style]}>{children}</View>;
}

// ------------------------------------------------------------------ text

export function T({
  children,
  variant = 'body',
  style,
  numberOfLines,
}: {
  children: ReactNode;
  variant?: 'title' | 'body' | 'strong' | 'muted' | 'small' | 'big';
  style?: StyleProp<TextStyle>;
  numberOfLines?: number;
}) {
  const t = useTheme();
  const base: Record<string, TextStyle> = {
    title: { fontSize: 18, fontWeight: '800', color: t.text },
    body: { fontSize: 15, color: t.text },
    strong: { fontSize: 15, fontWeight: '700', color: t.text },
    muted: { fontSize: 14, color: t.muted },
    small: { fontSize: 12.5, color: t.muted },
    big: { fontSize: 30, fontWeight: '800', color: t.text, letterSpacing: -0.5 },
  };
  return (
    <Text style={[base[variant], style]} numberOfLines={numberOfLines}>
      {children}
    </Text>
  );
}

export function ErrorText({ children }: { children: ReactNode }) {
  const t = useTheme();
  if (!children) return null;
  return (
    <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center', backgroundColor: t.dangerSoft, padding: 10, borderRadius: radius.sm }}>
      <Ionicons name="alert-circle" size={18} color={t.danger} />
      <Text style={{ color: t.danger, flex: 1 }}>{children}</Text>
    </View>
  );
}

// ------------------------------------------------------------------ controls

export function Button({
  title,
  onPress,
  variant = 'primary',
  icon,
  disabled,
  busy,
  size = 'md',
}: {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'danger' | 'success' | 'ghost';
  icon?: IconName;
  disabled?: boolean;
  busy?: boolean;
  size?: 'md' | 'lg';
}) {
  const t = useTheme();
  const palette = {
    primary: { bg: t.accent, fg: t.onAccent, border: t.accent },
    success: { bg: t.ok, fg: t.dark ? '#06210F' : '#FFFFFF', border: t.ok },
    danger: { bg: t.surface, fg: t.danger, border: t.danger },
    secondary: { bg: t.surface, fg: t.text, border: t.borderStrong },
    ghost: { bg: 'transparent', fg: t.accent, border: 'transparent' },
  }[variant];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: disabled || busy }}
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed }) => ({
        minHeight: size === 'lg' ? 56 : 48,
        borderRadius: radius.md,
        borderWidth: 1.5,
        borderColor: palette.border,
        backgroundColor: palette.bg,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        paddingHorizontal: 16,
        opacity: disabled || busy ? 0.5 : pressed ? 0.85 : 1,
        transform: [{ scale: pressed ? 0.99 : 1 }],
      })}
    >
      {busy ? (
        <ActivityIndicator color={palette.fg} />
      ) : (
        <>
          {icon ? <Ionicons name={icon} size={size === 'lg' ? 22 : 19} color={palette.fg} /> : null}
          <Text style={{ color: palette.fg, fontSize: size === 'lg' ? 17 : 15.5, fontWeight: '700' }}>{title}</Text>
        </>
      )}
    </Pressable>
  );
}

export function Chip({
  label,
  selected,
  onPress,
  icon,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
  icon?: IconName;
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        borderWidth: 1.5,
        borderColor: selected ? t.accent : t.border,
        backgroundColor: selected ? t.accentSoft : t.surface,
        borderRadius: radius.pill,
        paddingHorizontal: 13,
        paddingVertical: 8,
      }}
    >
      {icon ? <Ionicons name={icon} size={15} color={selected ? t.accent : t.muted} /> : null}
      <Text style={{ color: selected ? t.accent : t.text, fontWeight: selected ? '700' : '500' }}>{label}</Text>
    </Pressable>
  );
}

export function Chips({ children }: { children: ReactNode }) {
  return <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{children}</View>;
}

/** Two-to-four option switch, e.g. Shared seat / Exclusive vehicle. */
export function Segmented<V extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: V; label: string; icon: IconName; hint?: string }[];
  value: V;
  onChange: (v: V) => void;
}) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', gap: 10 }}>
      {options.map((o) => {
        const selected = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="button"
            accessibilityState={{ selected }}
            onPress={() => onChange(o.value)}
            style={{
              flex: 1,
              borderRadius: radius.md,
              borderWidth: 1.5,
              borderColor: selected ? t.accent : t.border,
              backgroundColor: selected ? t.accentSoft : t.surface,
              padding: 12,
              gap: 4,
            }}
          >
            <Ionicons name={o.icon} size={24} color={selected ? t.accent : t.muted} />
            <Text style={{ fontWeight: '700', color: selected ? t.accent : t.text }}>{o.label}</Text>
            {o.hint ? <Text style={{ fontSize: 12, color: t.muted }}>{o.hint}</Text> : null}
          </Pressable>
        );
      })}
    </View>
  );
}

export function Field({ label, icon, ...props }: { label: string; icon?: IconName } & TextInputProps) {
  const t = useTheme();
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ fontWeight: '600', color: t.text2, fontSize: 13.5 }}>{label}</Text>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: 8,
          borderWidth: 1.5,
          borderColor: t.border,
          borderRadius: radius.md,
          paddingHorizontal: 12,
          backgroundColor: t.surface2,
        }}
      >
        {icon ? <Ionicons name={icon} size={18} color={t.muted} /> : null}
        <TextInput
          placeholderTextColor={t.muted}
          style={{ flex: 1, paddingVertical: 12, fontSize: 16, color: t.text }}
          {...props}
        />
      </View>
    </View>
  );
}

// ------------------------------------------------------------------ display

export function Pill({ label, tone = 'neutral', icon }: { label: string; tone?: Tone; icon?: IconName }) {
  const t = useTheme();
  const c = toneColours(t, tone);
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 5,
        alignSelf: 'flex-start',
        backgroundColor: c.bg,
        borderRadius: radius.pill,
        paddingHorizontal: 10,
        paddingVertical: 4,
      }}
    >
      {icon ? <Ionicons name={icon} size={13} color={c.fg} /> : null}
      <Text style={{ color: c.fg, fontWeight: '700', fontSize: 12.5 }}>{label}</Text>
    </View>
  );
}

export function IconBadge({ icon, tone = 'accent', size = 40 }: { icon: IconName; tone?: Tone; size?: number }) {
  const t = useTheme();
  const c = toneColours(t, tone);
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 3.2,
        backgroundColor: c.bg,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Ionicons name={icon} size={size * 0.5} color={c.fg} />
    </View>
  );
}

export function Avatar({ name, size = 40 }: { name: string; size?: number }) {
  const t = useTheme();
  const initials = name
    .replace(/\(.*\)/, '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('');
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: t.navy,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Text style={{ color: '#FFFFFF', fontWeight: '800', fontSize: size * 0.38 }}>{initials}</Text>
    </View>
  );
}

/** A tappable list row with leading icon, title/subtitle and trailing content. */
export function ListRow({
  icon,
  tone = 'neutral',
  title,
  subtitle,
  right,
  onPress,
  selected,
}: {
  icon?: IconName;
  tone?: Tone;
  title: string;
  subtitle?: string;
  right?: ReactNode;
  onPress?: () => void;
  selected?: boolean;
}) {
  const t = useTheme();
  const content = (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        paddingVertical: 10,
        paddingHorizontal: 10,
        borderRadius: radius.md,
        backgroundColor: selected ? t.accentSoft : 'transparent',
      }}
    >
      {icon ? <IconBadge icon={icon} tone={selected ? 'accent' : tone} size={36} /> : null}
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 15, fontWeight: selected ? '700' : '600', color: t.text }} numberOfLines={1}>
          {title}
        </Text>
        {subtitle ? (
          <Text style={{ fontSize: 13, color: t.muted }} numberOfLines={2}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {right}
    </View>
  );
  if (!onPress) return content;
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ selected }} onPress={onPress}>
      {content}
    </Pressable>
  );
}

export function Divider() {
  const t = useTheme();
  return <View style={{ height: 1, backgroundColor: t.border, marginVertical: 2 }} />;
}

export function EmptyState({ icon, title, message, action }: { icon: IconName; title: string; message?: string; action?: ReactNode }) {
  const t = useTheme();
  return (
    <View style={{ alignItems: 'center', padding: 28, gap: 10 }}>
      <View style={{ width: 72, height: 72, borderRadius: 36, backgroundColor: t.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
        <Ionicons name={icon} size={34} color={t.accent} />
      </View>
      <Text style={{ fontSize: 17, fontWeight: '700', color: t.text, textAlign: 'center' }}>{title}</Text>
      {message ? <Text style={{ color: t.muted, textAlign: 'center', maxWidth: 300 }}>{message}</Text> : null}
      {action}
    </View>
  );
}

/** Horizontal progress through a ride's lifecycle. */
export function Stepper({ steps, current }: { steps: string[]; current: number }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-start' }}>
      {steps.map((label, i) => {
        const done = i < current;
        const active = i === current;
        const colour = done || active ? t.accent : t.borderStrong;
        return (
          <View key={label} style={{ flex: 1, alignItems: 'center' }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', alignSelf: 'stretch' }}>
              <View style={{ flex: 1, height: 3, backgroundColor: i === 0 ? 'transparent' : done || active ? t.accent : t.border }} />
              <View
                style={{
                  width: active ? 18 : 14,
                  height: active ? 18 : 14,
                  borderRadius: 9,
                  backgroundColor: done ? t.accent : t.surface,
                  borderWidth: 3,
                  borderColor: colour,
                }}
              />
              <View
                style={{ flex: 1, height: 3, backgroundColor: i === steps.length - 1 ? 'transparent' : done ? t.accent : t.border }}
              />
            </View>
            <Text
              style={{ fontSize: 11, marginTop: 5, textAlign: 'center', color: active ? t.accent : t.muted, fontWeight: active ? '700' : '500' }}
              numberOfLines={2}
            >
              {label}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

/** Large, spaced digits for an OTP the rider reads out to the driver. */
export function OtpDigits({ code }: { code: string }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', gap: 8 }} accessibilityLabel={`OTP ${code.split('').join(' ')}`}>
      {code.split('').map((d, i) => (
        <View
          key={i}
          style={{
            width: 46,
            height: 56,
            borderRadius: radius.md,
            backgroundColor: t.navy,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: 28, fontWeight: '800' }}>{d}</Text>
        </View>
      ))}
    </View>
  );
}

/** Pickup → drop visual with a dotted connector, like ride-hailing apps. */
export function RouteSummary({ from, to }: { from: string; to: string }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', gap: 12 }}>
      <View style={{ alignItems: 'center', paddingTop: 5 }}>
        <View style={{ width: 12, height: 12, borderRadius: 6, borderWidth: 3, borderColor: t.ok }} />
        <View style={{ width: 2, flex: 1, minHeight: 18, backgroundColor: t.border, marginVertical: 3 }} />
        <View style={{ width: 12, height: 12, borderRadius: 2, backgroundColor: t.accent }} />
      </View>
      <View style={{ flex: 1, justifyContent: 'space-between', gap: 12 }}>
        <Text style={{ fontSize: 15, fontWeight: '600', color: t.text }} numberOfLines={1}>
          {from}
        </Text>
        <Text style={{ fontSize: 15, fontWeight: '600', color: t.text }} numberOfLines={1}>
          {to}
        </Text>
      </View>
    </View>
  );
}

export function LiveDot() {
  const t = useTheme();
  return <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: t.ok }} />;
}
