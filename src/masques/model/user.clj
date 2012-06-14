(ns masques.model.user
  (:require [clj-record.boot :as clj-record-boot]
            [clj-crypto.core :as clj-crypto]
            [clojure.data.xml :as data-xml])
  (:use masques.model.base)
  (:import [java.sql Clob]
           [org.apache.commons.codec.binary Base64]))

(def saved-current-user (atom nil))

(def user-add-listeners (atom []))

(defn add-user-add-listener [user-add-listener]
  (swap! user-add-listeners conj user-add-listener))

(defn clob-clean-up 
  ([user key] 
    (let [clob (get user key)]
      (if (instance? Clob clob)
        (assoc user key (load-clob clob))
        user)))
  ([user key & keys]
    (reduce #(clob-clean-up %1 %2) user (conj keys key))))

(defn user-clean-up [user]
  (clob-clean-up user :public_key :private_key))

(defn password-str [password]
  (if (string? password)
    password
    (String. password)))

(defn encrypt-password [user]
  (let [salt (clj-crypto/create-salt)]
    (merge user
      { :encrypted_password (clj-crypto/encrypt-password-string (password-str (:password user)) salt
                              clj-crypto/default-encrypt-password-algorithm clj-crypto/default-encrypt-password-n)
        :salt (str salt)
        :encrypted_password_algorithm clj-crypto/default-encrypt-password-algorithm
        :encrypted_password_n clj-crypto/default-encrypt-password-n })))

(defn char-array-to-bytes [char-array-data]
  (byte-array (flatten (map #(seq (.getBytes (Character/toString %1) "UTF-8")) char-array-data))))

(defn char-array-to-string [char-array-data]
  (String. char-array-data))

(defn encrypt-private-key [password key-bytes]
  (Base64/encodeBase64String
    (clj-crypto/password-encrypt (char-array-to-string password) (Base64/encodeBase64String key-bytes)
      clj-crypto/default-symmetrical-algorithm)))

(defn generate-keys [user]
  (let [key-pair (clj-crypto/generate-key-pair)
        key-pair-map (clj-crypto/get-key-pair-map key-pair)]
    (merge user { :public_key (Base64/encodeBase64String (:bytes (:public-key key-pair-map)))
                  :public_key_algorithm (:algorithm (:public-key key-pair-map))
                  :private_key (encrypt-private-key (:password user) (:bytes (:private-key key-pair-map)))
                  :private_key_algorithm (:algorithm (:private-key key-pair-map))
                  :private_key_encryption_algorithm clj-crypto/default-symmetrical-algorithm })))

(defn generate-fields [user]
  (select-keys (generate-keys (encrypt-password user))
    [:name :encrypted_password :salt :encrypted_password_algorithm :encrypted_password_n :public_key
     :public_key_algorithm :private_key :private_key_algorithm :private_key_encryption_algorithm]))

(defn call-user-add-listeners [user]
  (doseq [user-add-listener @user-add-listeners]
    (user-add-listener user)))

(clj-record.core/init-model
  (:callbacks (:after-insert call-user-add-listeners)
              (:after-load user-clean-up)
              (:before-insert generate-fields)))

(defn all-users []
  (find-records [true]))

(defn all-user-names []
  (map :name (all-users)))

(defn find-user-by-name [user-name]
  (find-record { :name user-name }))

(defn validate-user-name [user-name]
  (when (and user-name (> (count user-name) 0) (not (find-user-by-name user-name)))
    user-name))

(defn char-arrays-equals? [array1 array2]
  (and (= (count array1) (count array2))
    (nil? (some #(not %1) (map #(= %1 %2) array1 array2)))))

(defn validate-passwords [password1 password2]
  (when (char-arrays-equals? password1 password2)
    password1))

(defn create-user [user-name password]
  (insert { :name user-name :password password }))

(defn encrypted-password [user password]
  (clj-crypto/encrypt-password-string (password-str password) (:salt user) (:encrypted_password_algorithm user)
    (:encrypted_password_n user)))

(defn login [user-name password]
  (when-let [user (find-user-by-name user-name)]
    (when (= (:encrypted_password user) (encrypted-password user password))
      (reset! saved-current-user (assoc user :password password))
      @saved-current-user)))

(defn logout []
  (reset! saved-current-user nil))

(defn current-user []
  @saved-current-user)

(defn decode-base64 [string]
  (when string
    (.decode (new Base64) string)))

(defn public-key-bytes [user]
  (decode-base64 (:public_key user)))

(defn public-key-map [user]
  { :algorithm (:public_key_algorithm user)  :bytes (public-key-bytes user) })

(defn private-key-bytes [user]
  (decode-base64
    (clj-crypto/password-decrypt (char-array-to-string (:password user)) (decode-base64 (:private_key user))
      (:private_key_encryption_algorithm user))))

(defn private-key-map [user]
  { :algorithm (:private_key_algorithm user) :bytes (private-key-bytes user) })

(defn current-user-key-pair []
  (let [user (current-user)
        public-key-map (public-key-map user)
        private-key-map (private-key-map user)]
    (clj-crypto/decode-key-pair { :public-key public-key-map :private-key private-key-map })))

(defn sign [data]
  (Base64/encodeBase64String (clj-crypto/sign (current-user-key-pair) data)))

(defn verify [data signature]
  (clj-crypto/verify-signature (current-user-key-pair) data (decode-base64 signature)))

(defn xml [user]
  (data-xml/element :user (select-keys user [:name :public_key :public_key_algorithm])))