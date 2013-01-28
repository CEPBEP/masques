(ns masques.controller.login.create-user
  (:require [clojure.tools.logging :as logging]
            [masques.controller.actions.utils :as actions-utils]
            [masques.controller.utils :as controller-utils]
            [masques.model.user :as user-model]
            [masques.view.login.create-user :as create-user-view]
            [seesaw.core :as seesaw-core]))

(defn attach-cancel-action [create-user-frame]
  (actions-utils/attach-window-close-listener create-user-frame "#cancel-button"))

(defn find-user-name-text [create-user-frame]
  (controller-utils/find-component create-user-frame "#user-name-text"))

(defn find-password-field1-text [create-user-frame]
  (controller-utils/find-component create-user-frame "#password-field1"))

(defn find-password-field2-text [create-user-frame]
  (controller-utils/find-component create-user-frame "#password-field2"))

(defn reset-password [password-field]
  (.setText password-field ""))

(defn create-user-error [create-user-frame message]
  (seesaw-core/alert message)
  (reset-password (find-password-field1-text create-user-frame))
  (reset-password (find-password-field2-text create-user-frame)))

(defn user-name [create-user-frame]
  (if-let [user-name (user-model/validate-user-name (seesaw-core/text (find-user-name-text create-user-frame)))]
    user-name
    (create-user-error create-user-frame "The user name you entered is invalid. Either you didn't enter a user name, or the one you entered is already taken.")))

(defn password1 [create-user-frame]
  (.getPassword (find-password-field1-text create-user-frame)))

(defn password2 [create-user-frame]
  (.getPassword (find-password-field2-text create-user-frame)))

(defn password [create-user-frame]
  (if-let [password (user-model/validate-passwords (password1 create-user-frame) (password2 create-user-frame))]
    password
    (create-user-error create-user-frame "The passwords you entered are not the same. Please enter them again.")))

(defn create-user-cleanup [create-user-frame user-id]
  (seesaw-core/invoke-later
    (if user-id
      (actions-utils/close-window create-user-frame)
      (controller-utils/enable-widget create-user-frame))))

(defn create-user [create-user-frame user-name password]
  (when (and user-name password)
    (future
      (try
        (create-user-cleanup create-user-frame (user-model/create-user user-name password))
        (catch Throwable t
          (logging/error t "An error occured while creating the user."))))))

(defn create-user-action [e]
  (let [create-user-frame (seesaw-core/to-frame e)]
    (controller-utils/disable-widget create-user-frame)
    (when-not (create-user create-user-frame (user-name create-user-frame) (password create-user-frame))
      (controller-utils/enable-widget create-user-frame))))

(defn attach-register-action [create-user-frame]
  (actions-utils/attach-listener create-user-frame "#register-button" create-user-action))

(defn find-register-button [create-user-frame]
  (seesaw-core/select create-user-frame ["#register-button"]))

(defn attach-default-button [create-user-frame]
  (actions-utils/set-default-button create-user-frame (find-register-button create-user-frame)))

(defn attach [create-user-frame]
  (attach-default-button (attach-register-action (attach-cancel-action create-user-frame))))

(defn show [login-frame]
  (controller-utils/show (attach (create-user-view/create login-frame))))