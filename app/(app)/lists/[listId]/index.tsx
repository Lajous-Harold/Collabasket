import { Stack, useLocalSearchParams } from 'expo-router';
import { ListDetailView } from '../../../../src/components/features/ListDetailView';
import { useNavColors } from '../../../../src/lib/theme';

export default function PersonalListDetailScreen() {
  const nav = useNavColors();
  const { listId, listName } = useLocalSearchParams<{
    listId: string;
    listName: string;
  }>();

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: listName || 'Liste',
          headerTintColor: nav.tint,
          headerStyle: { backgroundColor: nav.background },
          headerTitleStyle: { color: nav.text },
        }}
      />
      <ListDetailView listId={listId} listName={listName} />
    </>
  );
}
