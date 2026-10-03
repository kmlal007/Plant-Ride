import { LinearGradient } from 'expo-linear-gradient';
import { Redirect } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LogoMark } from '../components/Logo';
import { Button, Card, ErrorText, Field, T } from '../components/ui';
import { useAuth } from '../lib/auth';

export default function Login() {
  const { me, login } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (me) return <Redirect href="/shuttles" />;

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
              <LogoMark size={84} />
              <Text style={{ fontSize: 32, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.8 }}>
                Plant<Text style={{ color: '#F26B1D' }}>Ride</Text>
              </Text>
              <Text style={{ color: '#9FB3C8', fontSize: 15, textAlign: 'center' }}>
                Shuttles and rides inside the plant
              </Text>
            </View>
            <Card>
              <T variant="title">Sign in</T>
              <Field
                label="Employee code"
                icon="id-card-outline"
                autoCapitalize="characters"
                autoCorrect={false}
                value={loginId}
                onChangeText={setLoginId}
              />
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
            <Text style={{ color: '#7F93AA', textAlign: 'center', fontSize: 12 }}>
              Rides are free for employees and charged to your department.
            </Text>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </LinearGradient>
  );
}
