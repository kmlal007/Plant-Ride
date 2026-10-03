import { Redirect, Tabs } from 'expo-router';
import { colors } from '../../components/ui';
import { useAuth } from '../../lib/auth';

export default function TabsLayout() {
  const { ready, me } = useAuth();
  if (ready && !me) return <Redirect href="/login" />;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.accent,
        tabBarIcon: () => null,
        tabBarLabelStyle: { fontSize: 14, fontWeight: '600' },
        tabBarIconStyle: { display: 'none' },
      }}
    >
      <Tabs.Screen name="shuttles" options={{ title: 'Shuttles' }} />
      <Tabs.Screen name="book" options={{ title: 'Book ride' }} />
      <Tabs.Screen name="rides" options={{ title: 'My rides' }} />
      <Tabs.Screen name="approvals" options={{ title: 'Approvals' }} />
    </Tabs>
  );
}
