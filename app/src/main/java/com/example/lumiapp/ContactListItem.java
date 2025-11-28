package com.example.lumiapp;

public class ContactListItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_CONTACT = 1;

    private int type;
    private String headerTitle;
    private Contact contact;

    public static ContactListItem header(String title) {
        ContactListItem item = new ContactListItem();
        item.type = TYPE_HEADER;
        item.headerTitle = title;
        return item;
    }

    public static ContactListItem contact(Contact contact) {
        ContactListItem item = new ContactListItem();
        item.type = TYPE_CONTACT;
        item.contact = contact;
        return item;
    }

    public int getType() { return type; }
    public String getHeaderTitle() { return headerTitle; }
    public Contact getContact() { return contact; }
}
