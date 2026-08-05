import { Tabs, useRouter } from 'expo-router';
import { Text, View } from 'react-native';
import { useRealtimeLists } from '../../src/hooks/useRealtimeLists';
import { useRealtimeGroups } from '../../src/hooks/useRealtimeGroups';
import { useNotifications } from '../../src/hooks/useNotifications';
import { OfflineBanner } from '../../src/components/ui/OfflineBanner';
import { useNavColors } from '../../src/lib/theme';

export default function AppLayout() {
  const router = useRouter();
  const nav = useNavColors();
  // Realtime global — actif tant que l'utilisateur est connecte
  useRealtimeLists();
  useRealtimeGroups();
  // Push notifications — enregistrement token + listeners
  useNotifications();

  return (
    <View className="flex-1">
      <OfflineBanner />
      <Tabs
      screenOptions={{
        tabBarActiveTintColor: nav.tint,
        tabBarInactiveTintColor: nav.inactive,
        tabBarStyle: {
          backgroundColor: nav.background,
          borderTopColor: nav.border,
        },
        headerStyle: {
          backgroundColor: nav.background,
        },
        headerTitleStyle: {
          fontWeight: '600',
          color: nav.text,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Mes listes',
          tabBarIcon: ({ color }) => (
            <Text style={{ fontSize: 20, color }}>📝</Text>
          ),
        }}
        listeners={{
          tabPress: (e) => {
            e.preventDefault();
            router.navigate('/(app)/');
          },
        }}
      />
      <Tabs.Screen
        name="groups"
        options={{
          title: 'Groupes',
          tabBarIcon: ({ color }) => (
            <Text style={{ fontSize: 20, color }}>👥</Text>
          ),
        }}
        listeners={{
          tabPress: (e) => {
            e.preventDefault();
            router.navigate('/(app)/groups');
          },
        }}
      />
      <Tabs.Screen
        name="lists"
        options={{
          href: null,
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: 'Profil',
          tabBarIcon: ({ color }) => (
            <Text style={{ fontSize: 20, color }}>👤</Text>
          ),
        }}
      />
      </Tabs>
    </View>
  );
}
