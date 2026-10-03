import { Redirect } from 'expo-router';
import { useState } from 'react';
import { Button, Card, ErrorText, Field, Muted, Screen, Title } from '../components/ui';
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
    <Screen>
      <Title>Plant-Ride Driver</Title>
      <Muted>Sign in with your registered mobile number</Muted>
      <Card>
        <Field label="Mobile number" keyboardType="phone-pad" value={loginId} onChangeText={setLoginId} />
        <Field label="Password" secureTextEntry value={password} onChangeText={setPassword} onSubmitEditing={submit} />
        <ErrorText>{error}</ErrorText>
        <Button title="Sign in" onPress={submit} busy={busy} disabled={!loginId || !password} />
      </Card>
    </Screen>
  );
}
