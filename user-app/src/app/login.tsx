import { Redirect } from 'expo-router';
import { useState } from 'react';
import { Button, Card, ErrorText, Field, Muted, Screen, Title } from '../components/ui';
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
    <Screen>
      <Title>Plant-Ride</Title>
      <Muted>Shuttles and rides inside the plant</Muted>
      <Card>
        <Field label="Employee code" autoCapitalize="characters" autoCorrect={false} value={loginId} onChangeText={setLoginId} />
        <Field label="Password" secureTextEntry value={password} onChangeText={setPassword} onSubmitEditing={submit} />
        <ErrorText>{error}</ErrorText>
        <Button title="Sign in" onPress={submit} busy={busy} disabled={!loginId || !password} />
      </Card>
    </Screen>
  );
}
