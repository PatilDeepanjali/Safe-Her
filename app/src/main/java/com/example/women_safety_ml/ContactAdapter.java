package com.example.women_safety_ml;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    public interface OnContactActionListener {
        void onEdit(int position);
        void onDelete(int position);
        void onStarToggle(int position);
    }

    private List<Contact> contactList;
    private OnContactActionListener listener;

    public ContactAdapter(List<Contact> contactList, OnContactActionListener listener) {
        this.contactList = contactList;
        this.listener    = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = contactList.get(position);

        holder.tvName.setText(contact.getName());
        holder.tvPhone.setText(contact.getPhone());

        // Show/hide priority badge
        holder.tvPriority.setVisibility(contact.isPriority() ? View.VISIBLE : View.GONE);

        // Star icon state
        holder.ivStar.setImageResource(contact.isPriority()
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);

        // Click listeners
        holder.ivStar.setOnClickListener(v -> listener.onStarToggle(holder.getAdapterPosition()));
        holder.ivEdit.setOnClickListener(v -> listener.onEdit(holder.getAdapterPosition()));
        holder.ivDelete.setOnClickListener(v -> listener.onDelete(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() { return contactList.size(); }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView  tvName, tvPhone, tvPriority;
        ImageView ivStar, ivEdit, ivDelete;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tvContactName);
            tvPhone    = itemView.findViewById(R.id.tvContactPhone);
            tvPriority = itemView.findViewById(R.id.tvPriority);
            ivStar     = itemView.findViewById(R.id.ivStar);
            ivEdit     = itemView.findViewById(R.id.ivEdit);
            ivDelete   = itemView.findViewById(R.id.ivDelete);
        }
    }
}