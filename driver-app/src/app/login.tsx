import { LinearGradient } from 'expo-linear-gradient';
import { Redirect } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LogoMark } from '../components/Logo';
import { PartnerCredit } from '../components/PartnerCredit';
import { Button, Card, ErrorText, Field, T } from '../components/ui';
import { useAuth } from '../lib/auth';

export default function Login() {
  const { driver, login } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (driver) return <Redirect href="/home" />;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await login(loginId.trim(), password);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <LinearGradient colors={['#1B4469', '#0B1F33', '#07121E']} style={{ flex: 1 }}>
      <SafeAreaView style={{ flex: 1 }}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
          <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 20, gap: 24 }}>
            <View style={{ alignItems: 'center', gap: 12 }}>
              <LogoMark size={84} variant="driver" />
              <Text style={{ fontSize: 32, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.8 }}>
                Plant<Text style={{ color: '#F26B1D' }}>Ride</Text>
              </Text>
              <View style={{ backgroundColor: '#F26B1D', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 4 }}>
                <Text style={{ color: '#0B1F33', fontWeight: '800', letterSpacing: 2, fontSize: 12 }}>DRIVER</Text>
              </View>
              <PartnerCredit />
            </View>
            <Card>
              <T variant="title">Sign in</T>
              <Field label="Mobile number" icon="call-outline" keyboardType="phone-pad" value={loginId} onChangeText={setLoginId} />
              <Field
                label="Password"
                icon="lock-closed-outline"
                secureTextEntry
                value={password}
                onChangeText={setPassword}
                onSubmitEditing={submit}
              />
              <ErrorText>{error}</ErrorText>
              <Button title="Sign in" icon="log-in-outline" onPress={submit} busy={busy} disabled={!loginId || !password} size="lg" />
            </Card>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </LinearGradient>
  );
}
