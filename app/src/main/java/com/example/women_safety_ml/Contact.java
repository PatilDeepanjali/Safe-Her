package com.example.women_safety_ml;

public class Contact {
    private String name;
    private String phone;
    private boolean isPriority;
    private String documentId; // Firestore document ID

    public Contact(String name, String phone, boolean isPriority) {
        this.name       = name;
        this.phone      = phone;
        this.isPriority = isPriority;
    }

    public String getName()          { return name; }
    public String getPhone()         { return phone; }
    public boolean isPriority()      { return isPriority; }
    public String getDocumentId()    { return documentId; }

    public void setName(String n)        { this.name = n; }
    public void setPhone(String p)       { this.phone = p; }
    public void setPriority(boolean p)   { this.isPriority = p; }
    public void setDocumentId(String id) { this.documentId = id; }
}