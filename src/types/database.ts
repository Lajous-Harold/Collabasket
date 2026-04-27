// Ce fichier sera regénéré avec : supabase gen types typescript --linked > src/types/database.ts
// Pour l'instant, on définit les types manuellement basés sur le schéma SQL

export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[];

export type MembershipRole = 'owner' | 'admin' | 'member';
export type ListType = 'personal' | 'group';
export type StorageLocation = 'pantry' | 'fridge' | 'freezer';
export type InvitationStatus = 'pending' | 'accepted' | 'declined' | 'expired';

export interface Database {
  public: {
    Tables: {
      profiles: {
        Row: {
          id: string;
          display_name: string;
          phone_number: string | null;
          photo_url: string | null;
          created_at: string;
        };
        Insert: {
          id: string;
          display_name: string;
          phone_number?: string | null;
          photo_url?: string | null;
          created_at?: string;
        };
        Update: {
          id?: string;
          display_name?: string;
          phone_number?: string | null;
          photo_url?: string | null;
          created_at?: string;
        };
        Relationships: [];
      };
      devices: {
        Row: {
          id: string;
          user_id: string;
          fcm_token: string;
          platform: 'android' | 'ios';
          updated_at: string;
        };
        Insert: {
          id?: string;
          user_id: string;
          fcm_token: string;
          platform: 'android' | 'ios';
          updated_at?: string;
        };
        Update: {
          id?: string;
          user_id?: string;
          fcm_token?: string;
          platform?: 'android' | 'ios';
          updated_at?: string;
        };
        Relationships: [];
      };
      groups: {
        Row: {
          id: string;
          name: string;
          description: string | null;
          created_by: string;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          name: string;
          description?: string | null;
          created_by: string;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          name?: string;
          description?: string | null;
          created_by?: string;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      memberships: {
        Row: {
          id: string;
          user_id: string;
          group_id: string;
          role: MembershipRole;
          joined_at: string;
          nickname: string | null;
        };
        Insert: {
          id?: string;
          user_id: string;
          group_id: string;
          role?: MembershipRole;
          joined_at?: string;
          nickname?: string | null;
        };
        Update: {
          id?: string;
          user_id?: string;
          group_id?: string;
          role?: MembershipRole;
          joined_at?: string;
          nickname?: string | null;
        };
        Relationships: [];
      };
      lists: {
        Row: {
          id: string;
          name: string;
          type: ListType;
          owner_user_id: string | null;
          owner_group_id: string | null;
          is_archived: boolean;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          name: string;
          type: ListType;
          owner_user_id?: string | null;
          owner_group_id?: string | null;
          is_archived?: boolean;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          name?: string;
          type?: ListType;
          owner_user_id?: string | null;
          owner_group_id?: string | null;
          is_archived?: boolean;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      items: {
        Row: {
          id: string;
          list_id: string;
          name: string;
          category: string | null;
          unit: string | null;
          quantity: number;
          added_by: string;
          checked_by: string | null;
          is_checked: boolean;
          storage_location: StorageLocation | null;
          notes: string | null;
          price: number | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          list_id: string;
          name: string;
          category?: string | null;
          unit?: string | null;
          quantity?: number;
          added_by: string;
          checked_by?: string | null;
          is_checked?: boolean;
          storage_location?: StorageLocation | null;
          notes?: string | null;
          price?: number | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          list_id?: string;
          name?: string;
          category?: string | null;
          unit?: string | null;
          quantity?: number;
          added_by?: string;
          checked_by?: string | null;
          is_checked?: boolean;
          storage_location?: StorageLocation | null;
          notes?: string | null;
          price?: number | null;
          created_at?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      item_history: {
        Row: {
          id: string;
          user_id: string;
          name: string;
          category: string | null;
          unit: string | null;
          default_quantity: number | null;
          last_used_at: string;
          use_count: number;
          created_at: string;
        };
        Insert: {
          id?: string;
          user_id: string;
          name: string;
          category?: string | null;
          unit?: string | null;
          default_quantity?: number | null;
          last_used_at?: string;
          use_count?: number;
          created_at?: string;
        };
        Update: {
          id?: string;
          user_id?: string;
          name?: string;
          category?: string | null;
          unit?: string | null;
          default_quantity?: number | null;
          last_used_at?: string;
          use_count?: number;
          created_at?: string;
        };
        Relationships: [];
      };
      invitations: {
        Row: {
          id: string;
          group_id: string;
          invited_by: string;
          contact_identifier: string;
          token: string;
          status: InvitationStatus;
          expires_at: string;
          created_at: string;
        };
        Insert: {
          id?: string;
          group_id: string;
          invited_by: string;
          contact_identifier: string;
          token?: string;
          status?: InvitationStatus;
          expires_at?: string;
          created_at?: string;
        };
        Update: {
          id?: string;
          group_id?: string;
          invited_by?: string;
          contact_identifier?: string;
          token?: string;
          status?: InvitationStatus;
          expires_at?: string;
          created_at?: string;
        };
        Relationships: [];
      };
    };
    Views: Record<string, never>;
    Functions: {
      is_group_member: {
        Args: { p_group_id: string };
        Returns: boolean;
      };
      is_group_admin: {
        Args: { p_group_id: string };
        Returns: boolean;
      };
      can_access_list: {
        Args: { p_list_id: string };
        Returns: boolean;
      };
      accept_invitation: {
        Args: { p_token: string };
        Returns: { group_id: string; group_name: string }[];
      };
      update_my_nickname: {
        Args: { p_group_id: string; p_nickname: string };
        Returns: Database['public']['Tables']['memberships']['Row'];
      };
    };
    Enums: {
      membership_role: MembershipRole;
      list_type: ListType;
      storage_location: StorageLocation;
      invitation_status: InvitationStatus;
    };
    CompositeTypes: Record<string, never>;
  };
}
