import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, View } from 'react-native';
import { LogoMark } from '../components/Logo';
import { PartnerCredit } from '../components/PartnerCredit';
import { AuthProvider, useAuth } from '../lib/auth';

/** Screens fetch on mount, so they must not render before the stored session has been restored. */
function Navigator() {
  const { ready } = useAuth();
  if (!ready) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 24, backgroundColor: '#0B1F33' }}>
        <LogoMark size={88} variant="driver" />
        <ActivityIndicator color="#F26B1D" />
        <PartnerCredit />
      </View>
    );
  }
  return <Stack screenOptions={{ headerShown: false }} />;
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <StatusBar style="auto" />
      <Navigator />
    </AuthProvider>
  );
}
