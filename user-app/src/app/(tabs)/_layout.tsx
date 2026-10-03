import { Ionicons } from '@expo/vector-icons';
import { Redirect, Tabs } from 'expo-router';
import { ColorValue } from 'react-native';
import { useAuth } from '../../lib/auth';
import { useTheme } from '../../theme';

type IconName = React.ComponentProps<typeof Ionicons>['name'];

function icon(active: IconName, inactive: IconName) {
  return ({ focused, color, size }: { focused: boolean; color: ColorValue; size: number }) => (
    <Ionicons name={focused ? active : inactive} size={size} color={color as string} />
  );
}

export default function TabsLayout() {
  const { ready, me } = useAuth();
  const t = useTheme();
  if (ready && !me) return <Redirect href="/login" />;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: t.accent,
        tabBarInactiveTintColor: t.muted,
        tabBarStyle: { backgroundColor: t.surface, borderTopColor: t.border, height: 64, paddingTop: 6 },
        tabBarLabelStyle: { fontSize: 12, fontWeight: '600', paddingBottom: 6 },
      }}
    >
      <Tabs.Screen name="shuttles" options={{ title: 'Shuttles', tabBarIcon: icon('bus', 'bus-outline') }} />
      <Tabs.Screen name="book" options={{ title: 'Book', tabBarIcon: icon('car-sport', 'car-sport-outline') }} />
      <Tabs.Screen name="rides" options={{ title: 'My rides', tabBarIcon: icon('receipt', 'receipt-outline') }} />
      <Tabs.Screen
        name="approvals"
        options={{ title: 'Approvals', tabBarIcon: icon('checkmark-done-circle', 'checkmark-done-circle-outline') }}
      />
    </Tabs>
  );
}
