(ns masques.view.login.login
  (:require [clj-internationalization.core :as clj-i18n]
            [masques.view.utils :as view-utils]
            [seesaw.core :as seesaw-core])
  (:import [javax.swing JPasswordField]))

(defn create-user-name-panel []
  (seesaw-core/horizontal-panel :items
    [ (clj-i18n/user-name)
      [:fill-h 3]
      (seesaw-core/combobox :id :user-name-combobox :preferred-size [150 :by 25])
      [:fill-h 3]
      (seesaw-core/button :id :new-user-button :text (clj-i18n/new-user))]))

(defn password-field [& args]
  (let [password-field (new JPasswordField)]
    (when (and args (> (count args) 0))
      (apply seesaw-core/config! password-field args))
    password-field))

(defn create-password-panel []
  (seesaw-core/horizontal-panel :items
    [ (clj-i18n/password)
      [:fill-h 3]
      (password-field :id :password-field :preferred-size [120 :by 25])]))

(defn create-button-panel []
  (seesaw-core/border-panel :east
    (seesaw-core/horizontal-panel :items
      [(seesaw-core/button :id :login-button :text (clj-i18n/login))
       [:fill-h 3]
       (seesaw-core/button :id :cancel-button :text (clj-i18n/cancel))])))

(defn create-content []
  (seesaw-core/vertical-panel
    :border 5
    :items [(create-user-name-panel) [:fill-v 3] (create-password-panel) [:fill-v 5] (create-button-panel)]))

(defn create []
  (view-utils/center-window
    (seesaw-core/frame
      :title (clj-i18n/masques-login)
      :content (create-content)
      :visible? false)))