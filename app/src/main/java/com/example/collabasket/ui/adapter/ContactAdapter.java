package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Contact;

import java.util.ArrayList;
import java.util.List;

// ContactAdapter pour afficher la liste des contacts
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    private List<Contact> contactsList;
    private List<Contact> selectedContacts = new ArrayList<>();

    public ContactAdapter(List<Contact> contacts) {
        this.contactsList = contacts;
    }

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        Contact contact = contactsList.get(position);
        holder.contactName.setText(contact.getName());
        holder.contactPhone.setText(contact.getPhone());

        holder.checkbox.setChecked(contact.isSelected());

        holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            contact.setSelected(isChecked);
            if (isChecked) {
                selectedContacts.add(contact);
            } else {
                selectedContacts.remove(contact);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contactsList.size();
    }

    public List<Contact> getSelectedContacts() {
        return selectedContacts;
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {

        TextView contactName, contactPhone;
        CheckBox checkbox;

        public ContactViewHolder(View itemView) {
            super(itemView);
            contactName = itemView.findViewById(R.id.contact_name);
            contactPhone = itemView.findViewById(R.id.contact_phone);
            checkbox = itemView.findViewById(R.id.contact_checkbox);
        }
    }
}
