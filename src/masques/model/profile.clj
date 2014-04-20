(ns masques.model.profile
  (:refer-clojure :exclude [identity alias])
  (:require [clj-crypto.core :as clj-crypto]
            [clj-i2p.core :as clj-i2p]
            [clj-i2p.peer-service.persister-protocol :as persister-protocol]
            [clojure.java.io :as io]
            [config.db-config :as db-config]
            [masques.edn :as edn]
            [masques.model.avatar :as avatar-model]
            [masques.model.property :as property])
  (:use masques.model.base
        korma.core)
  (:import [org.apache.commons.codec.binary Base64]
           [java.io PushbackReader]))

(def current-user-id 1)

(def alias-key :alias)
(def avatar-path-key :avatar-path)
(def destination-key :destination)
(def identity-key :identity)
(def identity-algorithm-key :identity-algorithm)
(def private-key-key :private-key)
(def private-key-algorithm-key :private-key-algorithm)

(def saved-current-user (atom nil))

(def peer-update-listeners (atom []))

(def peer-delete-listeners (atom []))

(defn add-peer-update-listener [listener]
  (swap! peer-update-listeners conj listener))

(defn remove-peer-update-listener [listener]
  (swap! peer-update-listeners remove-listener listener))

(defn add-peer-delete-listener [listener]
  (swap! peer-delete-listeners conj listener))

(defn remove-peer-delete-listener [listener]
  (swap! peer-delete-listeners remove-listener listener))

(defn find-profile
  "Finds the profile with the given id."
  [record]
  (if (integer? record)
    (find-by-id profile record)
    (find-first profile record)))

(defn delete-profile
  "Deletes the given profile from the database. The profile should include the
id."
  [profile-record]
  (delete-record profile profile-record))

; CURRENT USER

(defn current-user
  "Returns the currently logged in user or nil if no user is logged in."
  []
  @saved-current-user)

(defn clean-user-data
  "Returns the current user cleaned for sending across the network."
  []
  (select-keys (current-user)
    [:alias :identity :identity-algorithm :destination]))
  
(defn set-current-user
  "Sets the currently logged in user."
  [profile]
  (reset! saved-current-user profile))
  
; SAVE PROFILE

(defn name-avatar [profile-record]
  (str (alias-key profile-record) "'s Avatar"))

(defn insert-avatar [profile-record]
  (let [avatar-file-map { :path (avatar-path-key profile-record) :name (name-avatar profile-record) }]
    (avatar-model/create-avatar-image (avatar-path-key profile-record))
    (insert-or-update file avatar-file-map)))

(defn save-avatar [profile-record]
  (if (avatar-path-key profile-record)
    (merge profile-record { :avatar-file-id (:id (insert-avatar profile-record)) })
    profile-record))

(defn save [record]
  (insert-or-update profile (dissoc (save-avatar record) :avatar avatar-path-key)))

(defn save-current-user [record]
  (when-not (find-by-id profile current-user-id)
    (save record)))

; BUILD PROFILE

(defn attach-avatar [profile-record]
  (if (:avatar-file-id profile-record)
    (conj { :avatar (find-by-id file (:avatar-file-id profile-record)) } profile-record)
    profile-record))

(defn build [id]
  (attach-avatar (find-by-id profile id)))

; CREATE USER

(defn generate-keys [profile-record]
  (let [key-pair (clj-crypto/generate-key-pair)
        key-pair-map (clj-crypto/get-key-pair-map key-pair)]
    (merge profile-record
      { :id current-user-id
        identity-key (Base64/encodeBase64String
                       (:bytes (:public-key key-pair-map)))
        identity-algorithm-key (:algorithm (:public-key key-pair-map))
        private-key-key (Base64/encodeBase64String
                          (:bytes (:private-key key-pair-map)))
        private-key-algorithm-key (:algorithm (:private-key key-pair-map)) })))

(defn create-user [user-name]
  (insert-record profile (generate-keys { alias-key user-name })))
  
(defn create-friend-profile
  "Creates a profile for a friend where you only have the alias, identity and identity algorithm."
  [alias identity identity-algorithm]
  (save { alias-key alias identity-key identity identity-algorithm-key identity-algorithm }))

(defn reload-current-user
  "Reloads the current user from the database. Returns the current user."
  []
  (find-profile current-user-id))

(defn logout
  "Logs out the currently logged in user. Just used for testing."
  []
  (set-current-user nil))

(defn create-masques-id-map
  "Creates a masques id map from the given profile. If the profile is not given,
then the current logged in profile is used."
  ([] (create-masques-id-map (current-user)))
  ([profile]
    (let [destination-map (if (clj-i2p/base-64-destination)
                            { destination-key (clj-i2p/base-64-destination) }
                            {})]
      (merge
        (select-keys profile [alias-key identity-key identity-algorithm-key])
        destination-map))))
           
(defn create-masques-id-file
  "Given a file and a profile, this function saves the profile as a masques id
to the file. If a profile is not given, then the currently logged in profile is
used."
  ([file] (create-masques-id-file file (current-user)))
  ([file profile]
    (edn/write file (create-masques-id-map profile))))

(defn read-masques-id-file 
  "Reads the given masques id file and returns the masques id map."
  [file]
  (edn/read file))

(defn identity
  "Returns the identity for the given profile."
  [profile]
  (identity-key profile))

(defn identity-algorithm
  "Returns the identity algorithm used for the given profile."
  [profile]
  (identity-algorithm-key profile))

(defn find-by-identity
  "Finds the profile with the given identity or identity map."
  ([identity-map]
    (find-by-identity (identity identity-map) (identity-algorithm identity-map)))
  ([identity identity-algorithm]
    (first
      (select profile
        (where { (h2-keyword identity-key) identity
                 (h2-keyword identity-algorithm-key) identity-algorithm })))))

(defn load-masques-id-map
  "Creates a profile from the given masques id map, saves it to the database,
and returns the new id. This function should not be directly called."
  [masques-id-map]
  (when masques-id-map
    (or (find-by-identity masques-id-map) (save masques-id-map))))

(defn load-masques-id-file
  "Creates a profile from the given masques id file, saves it to the database
and returns the new id. Do not call this function directly. Use the
send-request in friend_request instead."
  [file]
  (load-masques-id-map (read-masques-id-file file)))

(defn alias
  "Returns the alias for the given profile. If an integer is passed for the
profile, then it is used as the id of the profile to get."
  [profile]
  (if (integer? profile)
    (alias (find-profile profile))
    (alias-key profile)))

(defn destination
  "Returns the destination attached to the given profile."
  [profile]
  (destination-key profile))

(defn all-destinations
  "Returns all of the destinations of all the profiles."
  []
  (map destination-key
       (select profile (fields [(h2-keyword destination-key)]))))

(deftype DbPeerPersister []
  persister-protocol/PeerPersister
  (insert-peer [persister peer])

  (update-peer [persister peer])

  (delete-peer [persister peer])

  (all-peers [persister])

  (all-foreign-peers [persister])

  (find-peer [persister peer])

  (find-all-peers [persister peer])

  (last-updated-peer [persister])

  (all-unnotified-peers [persister])

  (all-notified-peers [persister])

  (add-peer-update-listener [persister listener]
    (add-peer-update-listener listener))

  (remove-peer-update-listener [persister listener]
    (remove-peer-update-listener listener))

  (add-peer-delete-listener [persister listener]
    (add-peer-delete-listener listener))

  (remove-peer-delete-listener [persister listener]
    (remove-peer-delete-listener listener))

  (default-destinations [persister]
    (all-destinations))

  (peers-downloaded? [persister]
    (property/peers-downloaded?))

  (set-peers-downloaded? [persister value]
    (property/set-peers-downloaded? value)))

(defn create-peer-persister
  "Creates a new instance of DbPeerPersister and returns it."
  []
  (DbPeerPersister.))

(defn init
  "Loads the currently logged in user's profile into memory. Creating the
profile if it does not alreay exist. Also, creates a new instance of
DbPeerPersister and registers it with the persister protocol if one is not
already registered."
  []
  (when (not (persister-protocol/protocol-registered?))
    (persister-protocol/register (create-peer-persister)))
  (if-let [logged-in-profile (find-profile current-user-id)]
    (set-current-user logged-in-profile)
    (let [user-name (db-config/current-username)]
      (if-let [new-profile (create-user user-name)]
        (set-current-user new-profile)
        (throw (RuntimeException.
                 (str "Could not create user: " user-name)))))))