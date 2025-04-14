package com.example.collabasket.ui.fragments;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Contact;
import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.ui.adapter.ProduitGroupesAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListeGroupesFragment extends Fragment {

    private String groupId, groupName;
    private FirebaseFirestore firestore;
    private ProduitGroupesAdapter produitAdapter;
    private RecyclerView recyclerView;
    private List<Contact> contactsList = new ArrayList<>();

    private static final int REQUEST_READ_CONTACTS = 101;

    private final String[] unitesDisponibles = { "pcs", "g", "kg", "ml", "L" };
    private final String[] categoriesDisponibles = {
            "Fruits et légumes", "Viandes et poissons", "Produits laitiers", "Boulangerie",
            "Épicerie sucrée", "Épicerie salée", "Boissons", "Surgelés",
            "Produits ménagers", "Hygiène et beauté", "Bébé", "Animaux",
            "Papeterie", "Textile", "Électronique", "Autre"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_liste_groupes, container, false);

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
            groupName = getArguments().getString("groupName");
        }

        TextView titre = rootView.findViewById(R.id.text_group_title);
        titre.setText(groupName);

        firestore = FirebaseFirestore.getInstance();

        recyclerView = rootView.findViewById(R.id.recycler_produits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        produitAdapter = new ProduitGroupesAdapter(groupId);
        recyclerView.setAdapter(produitAdapter);

        loadProduitsForGroup();

        FloatingActionButton fab = rootView.findViewById(R.id.fab_ajouter_groupe);
        fab.setOnClickListener(v -> showAddProduitDialog());

        ImageButton menuButton = rootView.findViewById(R.id.button_menu_options);
        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_groupe, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_ajouter_membre) {
                    checkContactsPermissionEtCharger();
                    return true;
                } else if (id == R.id.menu_info_groupe) {
                    InfoGroupeFragment infoGroupeFragment = new InfoGroupeFragment();
                    Bundle args = new Bundle();
                    args.putString("groupId", groupId);
                    infoGroupeFragment.setArguments(args);
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, infoGroupeFragment)
                            .addToBackStack(null)
                            .commit();
                    return true;
                } else if (id == R.id.menu_quitter_groupe) {
                    quitterGroupe();
                    return true;
                }
                return false;
            });

            popup.show();
        });

        return rootView;
    }

    private void checkContactsPermissionEtCharger() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_CONTACTS},
                    REQUEST_READ_CONTACTS
            );
        } else {
            loadContactsEtAfficherDialogue();
        }
    }

    private void loadContactsEtAfficherDialogue() {
        contactsList.clear();
        loadContacts();
        showContactInviteDialog();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContactsEtAfficherDialogue();
            } else {
                Toast.makeText(getContext(), "Permission refusée : impossible de lire les contacts", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadProduitsForGroup() {
        firestore.collection("groups")
                .document(groupId)
                .collection("produits")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ProduitGroupesAdapter.ProduitAvecId> produits = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ProduitGroupes produit = doc.toObject(ProduitGroupes.class);
                        produits.add(new ProduitGroupesAdapter.ProduitAvecId(doc.getId(), produit));
                    }
                    produitAdapter.setProduits(produits);
                });
    }

    private void loadContacts() {
        ContentResolver resolver = requireContext().getContentResolver();
        Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String phone = cursor.getString(phoneIndex);
                    contactsList.add(new Contact(name, phone));
                }
            } finally {
                cursor.close();
            }
            checkExistingUsers();
        }
    }

    private void checkExistingUsers() {
        for (Contact contact : contactsList) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("phone", contact.getPhone())
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            contact.setHasApp(true);
                        }
                    });
        }
    }

    private void showContactInviteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Sélectionnez des contacts à inviter");

        List<Contact> filtered = new ArrayList<>();
        for (Contact c : contactsList) {
            if (c.isHasApp()) filtered.add(c);
        }

        String[] noms = new String[filtered.size()];
        boolean[] checked = new boolean[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            noms[i] = filtered.get(i).getName() + " - " + filtered.get(i).getPhone();
            checked[i] = false;
        }

        builder.setMultiChoiceItems(noms, checked, (dialog, which, isChecked) -> {
            filtered.get(which).setSelected(isChecked);
        });

        builder.setPositiveButton("Inviter", (dialog, which) -> {
            for (Contact c : filtered) {
                if (c.isSelected()) {
                    sendInvitation(c, groupId);
                }
            }
        });

        builder.setNegativeButton("Annuler", null);
        builder.show();
    }

    private void sendInvitation(Contact contact, String groupId) {
        String link = FirebaseDynamicLinks.getInstance()
                .createDynamicLink()
                .setLink(Uri.parse("https://example.com/invite?groupId=" + groupId))
                .setDomainUriPrefix("https://xyz.page.link")
                .buildDynamicLink()
                .getUri()
                .toString();

        String message = "Rejoignez notre groupe sur Collabasket : " + link;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + contact.getPhone()));
        intent.putExtra("sms_body", message);
        startActivity(intent);
    }

    private void quitterGroupe() {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Map<String, Object>> members = (List<Map<String, Object>>) snapshot.get("members");
                    List<String> memberIds = (List<String>) snapshot.get("memberIds");

                    String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    final String[] currentRole = { "Membre" };

                    for (Map<String, Object> membre : members) {
                        if (currentUid.equals(membre.get("userId"))) {
                            currentRole[0] = membre.get("role") != null ? membre.get("role").toString() : "Membre";
                            break;
                        }
                    }

                    new AlertDialog.Builder(getContext())
                            .setTitle("Quitter le groupe")
                            .setMessage(currentRole[0].equals("Propriétaire")
                                    ? "Vous êtes le Propriétaire. En quittant, le groupe sera supprimé pour tous. Confirmez-vous ?"
                                    : "Souhaitez-vous vraiment quitter ce groupe ?")
                            .setPositiveButton("Oui", (dialog, which) -> {
                                if (currentRole[0].equals("Propriétaire")) {
                                    FirebaseFirestore.getInstance()
                                            .collection("groups")
                                            .document(groupId)
                                            .delete()
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(getContext(), "Groupe supprimé", Toast.LENGTH_SHORT).show();
                                                requireActivity().getSupportFragmentManager()
                                                        .beginTransaction()
                                                        .replace(R.id.fragment_container, new GroupesFragment())
                                                        .commit();
                                            });
                                } else {
                                    members.removeIf(m -> currentUid.equals(m.get("userId")));
                                    memberIds.removeIf(id1 -> id1.equals(currentUid));

                                    FirebaseFirestore.getInstance()
                                            .collection("groups")
                                            .document(groupId)
                                            .update("members", members, "memberIds", memberIds)
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(getContext(), "Vous avez quitté le groupe", Toast.LENGTH_SHORT).show();
                                                requireActivity().getSupportFragmentManager()
                                                        .beginTransaction()
                                                        .replace(R.id.fragment_container, new GroupesFragment())
                                                        .commit();
                                            });
                                }
                            })
                            .setNegativeButton("Annuler", null)
                            .show();
                });
    }

    private void showAddProduitDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ajout_produit_groupes, null);

        EditText editNom = dialogView.findViewById(R.id.edit_nom);
        EditText editQuantite = dialogView.findViewById(R.id.edit_quantite);
        Spinner spinnerUnite = dialogView.findViewById(R.id.spinner_unite);
        Spinner spinnerCategorie = dialogView.findViewById(R.id.spinner_categorie);

        spinnerUnite.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, unitesDisponibles));

        spinnerCategorie.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, categoriesDisponibles));

        new AlertDialog.Builder(getContext())
                .setTitle("Ajouter un produit")
                .setView(dialogView)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String nom = editNom.getText().toString().trim();
                    String qteText = editQuantite.getText().toString().trim();
                    String unite = spinnerUnite.getSelectedItem().toString();
                    String categorie = spinnerCategorie.getSelectedItem().toString();

                    float quantite;
                    try {
                        quantite = Float.parseFloat(qteText);
                    } catch (NumberFormatException e) {
                        quantite = 1f;
                    }

                    if (!nom.isEmpty()) {
                        final float finalQuantite = quantite;
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        firestore.collection("users")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    String ajoutePar = doc.getString("username");
                                    if (ajoutePar == null || ajoutePar.isEmpty()) {
                                        ajoutePar = "Inconnu";
                                    }

                                    ProduitGroupes produit = new ProduitGroupes(nom, categorie, finalQuantite, unite, groupId, ajoutePar);

                                    firestore.collection("groups")
                                            .document(groupId)
                                            .collection("produits")
                                            .add(produit)
                                            .addOnSuccessListener(ref -> loadProduitsForGroup());
                                });
                    } else {
                        Toast.makeText(getContext(), "Le nom du produit est requis", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
