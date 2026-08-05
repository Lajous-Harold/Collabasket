import { Stack, useLocalSearchParams } from 'expo-router';
import { ListDetailView } from '../../../../../../src/components/features/ListDetailView';
import { useNavColors } from '../../../../../../src/lib/theme';

export default function GroupListDetailScreen() {
  const nav = useNavColors();
  const { listId, listName, groupName, groupId } = useLocalSearchParams<{
    groupId: string;
    listId: string;
    listName: string;
    groupName: string;
  }>();

  const headerTitle = groupName
    ? `${groupName} / ${listName || 'Liste'}`
    : listName || 'Liste';

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: headerTitle,
          headerTintColor: nav.tint,
          headerStyle: { backgroundColor: nav.background },
          headerTitleStyle: { color: nav.text },
        }}
      />
      <ListDetailView listId={listId} listName={listName} groupId={groupId} />
    </>
  );
}
