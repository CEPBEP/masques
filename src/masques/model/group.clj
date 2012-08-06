(ns masques.model.group
  (:require [clj-record.boot :as clj-record-boot]
            [clojure.string :as string]
            [masques.model.identity :as identity]
            [masques.model.group-permission :as group-permission]
            [masques.model.permission :as permission])
  (:use masques.model.base))

(def acquaintances-group-name "Acquaintances")
(def best-friends-group-name "Best Friends")
(def enemies-group-name "Enemies")
(def family-group-name "Family")
(def friends-group-name "Friends")

(def default-groups [acquaintances-group-name best-friends-group-name enemies-group-name family-group-name friends-group-name])

(clj-record.core/init-model
  (:associations (has-many group-memberships)
                 (has-many group-permissions)))

(defn find-group [group]
  (cond
    (string? group) (find-record { :name group :identity_id (identity/current-user-identity-id) })
    (map? group) (if-let [group-id (:id group)] (find-group group-id) (find-record group))
    (integer? group) (find-record { :id group })
    :else (throw (RuntimeException. (str "Don't know how to get a group for type: " (type group))))))

(defn group-id [group]
  (cond
    (integer? group) group
    (map? group) (if-let [group-id (:id group)] group-id (:id (find-group group)))
    :else (:id (find-group group))))

(defn filter-ids [id-list]
  (filter integer? id-list))

(defn sql-list [value-list]
  (str "(" (string/join "," value-list) ")"))

(defn find-groups [groups]
  (find-by-sql [(str "SELECT * FROM groups WHERE identity_id = ? AND id IN " (sql-list (filter-ids (map group-id groups))))
                (identity/current-user-identity-id)]))

(defn find-identity-groups [identity]
  (find-records { :identity_id (:id identity) }))

(defn add-read-permission [group permission]
  (group-permission/add-read-permission-to-group (group-id group) (permission/permission-id permission)))

(defn add-write-permission [group permission]
  (group-permission/add-write-permission-to-group (group-id group) (permission/permission-id permission)))

(defn has-read-permission? [group permission]
  (group-permission/has-read-permission? (group-id group) (permission/permission-id permission)))

(defn has-write-permission? [group permission]
  (group-permission/has-write-permission? (group-id group) (permission/permission-id permission)))

(defn remove-read-permission [group permission]
  (group-permission/remove-read-permission-from-group (group-id group) (permission/permission-id permission)))

(defn remove-write-permission [group permission]
  (group-permission/remove-write-permission-from-group (group-id group) (permission/permission-id permission)))

(defn any-group-has-read-permission? [groups permission]
  (group-permission/any-group-has-read-permission? (map group-id groups) (permission/permission-id permission)))

(defn any-group-has-write-permission? [groups permission]
  (group-permission/any-group-has-write-permission? (map group-id groups) (permission/permission-id permission)))

(defn add-default-groups [identity]
  (when (identity/find-user identity)
    (doseq [default-group default-groups]
      (insert { :name default-group :identity_id (:id identity) :user_generated 1 }))))

(defn remove-deleted-identity-groups [identity]
  (doseq [group (find-identity-groups identity)]
    (destroy-record group)))

(defn init []
  (identity/add-identity-add-listener add-default-groups)
  (identity/add-identity-delete-listener remove-deleted-identity-groups))