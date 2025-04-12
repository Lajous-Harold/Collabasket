package com.example.collabasket.model;

public class Contact {

    private String name;
    private String phone;
    private boolean hasApp;
    private boolean isSelected;

    // Constructeur
    public Contact(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.hasApp = false;
        this.isSelected = false;
    }

    // Getters et setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isHasApp() {
        return hasApp;
    }

    public void setHasApp(boolean hasApp) {
        this.hasApp = hasApp;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
