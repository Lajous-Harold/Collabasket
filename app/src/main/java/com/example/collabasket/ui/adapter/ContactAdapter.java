package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Contact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> implements Filterable {

    private List<Contact> originalList;
    private List<Contact> filteredList;
    private List<Contact> selectedContacts = new ArrayList<>();

    public ContactAdapter(List<Contact> contacts) {
        this.originalList = new ArrayList<>(contacts);
        this.filteredList = new ArrayList<>(contacts);
        sortFilteredList();
    }

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        Contact contact = filteredList.get(position);
        holder.contactName.setText(contact.getName() + (contact.isHasApp() ? "  ✔" : ""));
        holder.contactPhone.setText(contact.getPhone());
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(contact.isSelected());

        holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            contact.setSelected(isChecked);
            if (isChecked && !selectedContacts.contains(contact)) {
                selectedContacts.add(contact);
            } else if (!isChecked) {
                selectedContacts.remove(contact);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public List<Contact> getSelectedContacts() {
        return selectedContacts;
    }

    private void sortFilteredList() {
        Collections.sort(filteredList, Comparator.comparing(Contact::isHasApp).reversed());
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<Contact> resultList = new ArrayList<>();
                String query = constraint.toString().toLowerCase().replaceAll("\\s", "");

                if (query.isEmpty()) {
                    resultList.addAll(originalList);
                } else {
                    for (Contact contact : originalList) {
                        String name = contact.getName() != null ? contact.getName().toLowerCase() : "";
                        String phone = contact.getPhone() != null ? contact.getPhone().replaceAll("\\s", "") : "";
                        if (name.contains(query) || phone.contains(query)) {
                            resultList.add(contact);
                        }
                    }
                }

                FilterResults results = new FilterResults();
                results.values = resultList;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList.clear();
                filteredList.addAll((List<Contact>) results.values);
                sortFilteredList();
                notifyDataSetChanged();
            }
        };
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView contactName;
        TextView contactPhone;
        CheckBox checkbox;

        public ContactViewHolder(View itemView) {
            super(itemView);
            contactName = itemView.findViewById(R.id.contact_name);
            contactPhone = itemView.findViewById(R.id.contact_phone);
            checkbox = itemView.findViewById(R.id.contact_checkbox);
        }
    }
}
