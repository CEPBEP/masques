(ns masques.model.base
  (:use korma.core)
  (:require [clj-time.core :as clj-time]
            [clj-time.coerce :as clj-time-coerce]
            [clojure.string :as string]
            [clojure.tools.logging :as logging]
            [drift-db.core :as drift-db]
            [masques.core :as masques-core]
            [korma.db :as korma-db] )
  (:import [java.sql Clob])
  (:import [java.util.UUID]))

; Database connections. db is for non-korma stuff.
(def db (deref masques-core/db))
(korma-db/defdb mydb (drift-db/db-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Data sanitation: Clob helpers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clob-string [clob]
  (when clob
    (let [clob-stream (.getCharacterStream clob)]
      (try
        (string/join "\n" (take-while identity (repeatedly #(.readLine clob-stream))))
        (catch Exception e
          (logging/error (str "An error occured while reading a clob: " e)))))))

(defn get-clob [record clob-key]
  (when-let [clob (clob-key record)]
    (when (instance? Clob clob)
      clob)))

(defn clean-clob-key [record clob-key]
  (if-let [clob (get-clob record clob-key)]
    (assoc record clob-key (clob-string clob))
    record))

(defn clean-clob [record field-name field-data]
    (assoc record field-name (clob-string field-data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Data sanitation: Date/time helpers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn h2-to-date-time [h2-date-time]
  (clj-time-coerce/to-date-time h2-date-time))

(defn clean-date-time-field [record field-name field-data]
  (conj record {field-name (h2-to-date-time field-data)}))

(defn set-created-at [record]
  (conj record {:CREATED_AT (str (clj-time/now))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; UUID helpers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn uuid [] 
  (str (java.util.UUID/randomUUID)))

(defn set-uuid [record]
  (conj record {:UUID (uuid)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Turn h2 keywords (:THAT_LOOK_LIKE_THIS) into clojure-style
; keywords (:that-look-like-this) and back again.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn lower-case [the-string]
  (string/lower-case the-string))

(defn upper-case [the-string]
  (string/upper-case the-string))

(defn remove-colon [the-string]
  (string/replace the-string ":" ""))

(defn replace-underscores-with-hyphens [has-underscores]
  (string/replace has-underscores "_" "-"))

(defn replace-hyphens-with-underscores [has-hyphens]
  (string/replace has-hyphens "-" "_"))

(defn clojure-keyword [h2-keyword]
  (let [lower-string (str h2-keyword)
        no-colon (remove-colon lower-string)
        no-underscores (replace-underscores-with-hyphens no-colon)
        lower-cased (string/lower-case no-underscores)]
    (keyword lower-cased)))

(defn h2-keyword [clojure-keyword]
  (let [no-colon (str (remove-colon clojure-keyword))
        no-hyphens (replace-hyphens-with-underscores no-colon)
        upper-cased (string/upper-case no-hyphens)]
    (keyword upper-cased)))

(defn remove-item [item record]
  (dissoc (into {} record) item))

(defn clean-field-data [record field-name]
  "Prepares H2 data, from the database, for the rest of our clojure application."
  (let [field-data (get record field-name)]
    (cond
      ; (nil? field-data)
        ; (remove-item field-name record)
      (instance? Clob field-data)
        (clean-clob record field-name field-data)
      (instance? java.sql.Timestamp field-data)
        (clean-date-time-field record field-name field-data)
      :else record)))

(defn clojure-field-name [record field-name]
  (let [field-data (get record field-name)
        ready-map (remove-item field-name record)]
    (assoc ready-map (clojure-keyword field-name) field-data)))

(defn h2-field-name [record field-name]
  (let [field-data (get record field-name)
        ready-map (remove-item field-name record)]
    (assoc ready-map (h2-keyword field-name) field-data)))

(defn clean-up-for-clojure [record]
  (let [clean-data (reduce clean-field-data record (keys record))]
    (reduce clojure-field-name clean-data (keys clean-data))))

(defn clean-up-for-h2 [record]
  (let [clean-data (reduce clean-field-data record (keys record))]
    (reduce h2-field-name clean-data (keys clean-data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; SQL/ORM helpers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-by-id [entity id]
  (clean-up-for-clojure (first (select entity (where {:ID id})))))

(defn insert-record [entity record]
  (println (str "before insert: " record))
  (let [id (vals (insert entity (values record)))]
    (println "the insert just happened")
    (println "after insert: " (find-by-id entity id))
    (find-by-id entity id)))

(defn update-record [entity record]
  (update entity
    (set-fields record)
    (where {:ID (:id record)})))

(defn insert-or-update [entity record]
  (let [h2-record (clean-up-for-h2 record)]
    (if (:ID h2-record)
      (update-record entity h2-record)
      (insert-record entity h2-record))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Entities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; ALBUM
(defn prepare-album-for-h2 [record]
  (set-created-at record))
(defn transform-album-for-clojure [record]
  (let [clean (clean-up-for-clojure record)]
    clean))
(defentity album
  (prepare prepare-album-for-h2)
  (transform transform-album-for-clojure)
  (table :ALBUM))

; FILE
(defn prepare-file-for-h2 [record]
  (set-created-at record))
(defentity file
  (prepare prepare-file-for-h2)
  (transform clean-up-for-clojure)
  (table :FILE))

; FRIEND
(defn prepare-friend-for-h2 [record]
  (set-created-at record))
(defentity friend 
  (prepare prepare-friend-for-h2)
  (transform clean-up-for-clojure)
  (table :FRIEND))

; GROUPING
(defn prepare-grouping-for-h2 [record]
  (set-created-at record))
(defentity grouping 
  (transform clean-up-for-clojure)
  (prepare prepare-grouping-for-h2)
  (table :GROUPING))

; GROUPING_PROFILE
(defn prepare-grouping-profile-for-h2 [record]
  (set-created-at record))
(defentity grouping-profile
  (transform clean-up-for-clojure)
  (prepare prepare-grouping-profile-for-h2)
  (table :GROUPING_PROFILE))

; LOG
(defn prepare-log-for-h2 [record]
  record)
(defentity log
  (prepare prepare-log-for-h2)
  (transform clean-up-for-clojure)
  (table :LOG))

; MESSAGE
(defn prepare-message-for-h2 [record]
  record)
(defentity message
  (prepare prepare-message-for-h2)
  (transform clean-up-for-clojure)
  (table :MESSAGE))

; PROFILE
(defn prepare-profile-for-h2 [record]
  (set-created-at record))
(defentity profile
  (transform clean-up-for-clojure)
  (prepare prepare-profile-for-h2)
  (table :PROFILE))

; SHARE
(defn prepare-share-for-h2 [record]
  (set-uuid (set-created-at record)))
(defentity share
  (transform clean-up-for-clojure)
  (prepare prepare-share-for-h2)
  (table :SHARE))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Other randomness, used in models.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-listener [listeners listener-to-remove]
  (remove #(= listener-to-remove %) listeners))

(defn as-boolean [value]
  (and value (not (= value 0))))

