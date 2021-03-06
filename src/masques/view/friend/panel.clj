(ns masques.view.friend.panel
  (:require [clj-internationalization.term :as term]
            [clojure.tools.logging :as logging]
            [clojure.tools.string-utils :as string-utils]
            [masques.model.friend-request :as friend-request-model]
            [masques.model.profile :as profile-model]
            [masques.service.calls.friend :as friend-call]
            [masques.service.calls.unfriend :as unfriend-call]
            [masques.view.friend.all-friends-table-model
             :as all-friends-table-model]
            [masques.view.friend.my-requests-table-model
             :as my-requests-table-model]
            [masques.view.friend.send-friend-request-panel 
             :as send-friend-request-panel]
            [masques.view.friend.sent-friend-request-table-model
             :as sent-friend-request-table-model]
            [masques.view.friend.utils :as friend-utils]
            [masques.view.utils :as view-utils]
            [masques.view.utils.button-table-cell-editor
             :as button-table-cell-editor]
            [masques.view.utils.korma-table-model :as korma-table-model]
            [masques.view.utils.table-renderer :as table-renderer]
            [seesaw.border :as seesaw-border]
            [seesaw.color :as seesaw-color]
            [seesaw.core :as seesaw-core])
  (:import [javax.swing JOptionPane]
           [java.io File]))

(def export-masque-button-id :export-masque-button)
(def export-masque-button-listener-key :export-masque-button-listener)

(def send-friend-request-button-id :send-friend-request-button)
(def send-friend-request-button-listener-key
  :send-friend-request-button-listener)

(def main-panel-key :main-panel-key)
(def send-friend-request-panel-key :send-friend-request-panel-key)

(def all-friends-table-id :all-friends-table)
(def my-requests-table-id :my-requests-table)
(def sent-requests-table-id :sent-requests-table)

(defn create-search-fields-panel []
  (seesaw-core/vertical-panel
    :items [(seesaw-core/border-panel
              :west (seesaw-core/horizontal-panel
                      :items [(term/friend)
                              [:fill-h 5]
                              (seesaw-core/text :id :alias-search-text
                                                :columns 10)
                              [:fill-h 5]
                              (term/alias-or-nick)])
              :east (view-utils/create-under-construction-link-button 
                      :id :clear-search-button :text (term/clear-search)))
            (seesaw-core/border-panel
              :west (seesaw-core/horizontal-panel
                      :items [(term/group)
                              [:fill-h 5]
                              (seesaw-core/combobox
                                :id :group-search-combobox
                                :preferred-size  [100 :by 20])])
              :center (seesaw-core/horizontal-panel
                        :items [(term/added)
                                [:fill-h 5]
                                (seesaw-core/text :id :added-to-1-search-text
                                                  :columns 10)
                                [:fill-h 5]
                                (term/to)
                                [:fill-h 5]
                                (seesaw-core/text :id :added-to-2-search-text
                                                  :columns 10)])
              :east (view-utils/create-under-construction-link-button
                      :id :search-friends-button :text (term/search-friends))
              :hgap 15)]
    
    :border [15 (seesaw-border/line-border :thickness 1)]))

(defn create-search-panel []
  (seesaw-core/border-panel
    :center (create-search-fields-panel)
    :south (seesaw-core/border-panel
             :west (view-utils/create-under-construction-link-button
                     :id :advanced-friend-search-button
                     :text (term/advanced-friend-search)))
    
    :vgap 5))

(defn create-unfriend-request-listener
  "Creates a listener for the unfriend button in the sent requests table."
  [table]
  (fn [event]
    (let [button (seesaw-core/to-widget event)
          request-id (button-table-cell-editor/value-from button)
          choice
          (seesaw-core/input
             button 
             (term/are-you-sure-you-want-to-unfriend
               (profile-model/alias
                 (friend-request-model/find-to-profile request-id)))
             :choices [(term/unfriend)])]
      (when (= choice (term/unfriend))
        (future
          (unfriend-call/send-unfriend request-id))))))

(defn create-all-friends-table []
  (let [all-friends-table (seesaw-core/table
                            :id all-friends-table-id
                            :model (all-friends-table-model/create)
                            :auto-resize :all-columns)]
    (table-renderer/set-button-table-cell-renderer all-friends-table 0
      table-renderer/image-cell-renderer 36)
    (table-renderer/set-button-table-cell-renderer all-friends-table 4
      all-friends-table-model/profile-button-cell-renderer)
    (table-renderer/set-button-table-cell-renderer all-friends-table 5
      all-friends-table-model/shares-button-cell-renderer)
    (table-renderer/set-button-table-cell-renderer all-friends-table 6
      all-friends-table-model/unfriend-button-cell-renderer)
    (button-table-cell-editor/set-cell-editor 
      all-friends-table 6 (term/unfriend)
      (create-unfriend-request-listener all-friends-table))
    (.setRowHeight all-friends-table 32)
    (seesaw-core/scrollable all-friends-table)))

(defn create-all-friends-tab []
  (seesaw-core/border-panel
    :north (seesaw-core/border-panel
             :west (view-utils/create-link-button
                     :id send-friend-request-button-id
                     :text (term/plus-send-friend-request))
             :east (view-utils/create-link-button
                     :id export-masque-button-id
                     :text (term/export-masque)))
    :center (create-all-friends-table)

    :vgap 5
    :border 15))

(defn create-accept-request-listener
  "Creates a listener for the reject button in the my requests table."
  [table]
  (fn [event]
    (let [button (seesaw-core/to-widget event)
          request-id (button-table-cell-editor/value-from button)]
      (future
        (friend-call/send-friend request-id)))))

(defn create-reject-request-listener
  "Creates a listener for the reject button in the my requests table."
  [table]
  (fn [event]
    (let [button (seesaw-core/to-widget event)
          request-id (button-table-cell-editor/value-from button)]
      (future
        (unfriend-call/send-unfriend request-id)))))

(defn create-my-requests-table []
  (let [my-requests-table (seesaw-core/table
                            :id my-requests-table-id
                            :model (my-requests-table-model/create)
                            :auto-resize :all-columns)]
    (table-renderer/set-button-table-cell-renderer my-requests-table 0
      table-renderer/image-cell-renderer 36)
    (table-renderer/set-button-table-cell-renderer my-requests-table 3
      my-requests-table-model/accept-button-cell-renderer)
    (table-renderer/set-button-table-cell-renderer my-requests-table 4
      my-requests-table-model/reject-button-cell-renderer)
    (button-table-cell-editor/set-cell-editor 
      my-requests-table 3 (term/accept)
      (create-accept-request-listener my-requests-table))
    (button-table-cell-editor/set-cell-editor 
      my-requests-table 4 (term/reject)
      (create-reject-request-listener my-requests-table))
    (.setRowHeight my-requests-table 32)
    (seesaw-core/scrollable my-requests-table)))

(defn create-my-requests-tab []
  (seesaw-core/border-panel
    :center (create-my-requests-table)

    :border 15))

(defn create-sent-requests-table []
  (let [sent-requests-table (seesaw-core/table
                              :id sent-requests-table-id
                              :model (sent-friend-request-table-model/create)
                              :auto-resize :all-columns)]
    (table-renderer/set-button-table-cell-renderer sent-requests-table 0
      table-renderer/image-cell-renderer 36)
    (table-renderer/set-button-table-cell-renderer sent-requests-table 3
      sent-friend-request-table-model/unfriend-button-cell-renderer)
    (button-table-cell-editor/set-cell-editor 
      sent-requests-table 3 (term/unfriend)
      (create-unfriend-request-listener sent-requests-table))
    (.setRowHeight sent-requests-table 32)
    (seesaw-core/scrollable sent-requests-table)))

(defn create-sent-requests-tab []
  (seesaw-core/border-panel
    :center (create-sent-requests-table)

    :border 15))

(defn create-body-panel []
  (seesaw-core/tabbed-panel
    :tabs [{ :title (term/all-friends) :content (create-all-friends-tab) }
           { :title (term/my-requests) :content (create-my-requests-tab) }
           { :title (term/sent-requests) 
             :content (create-sent-requests-tab) }]))

(defn create-main-panel []
  (seesaw-core/border-panel
    :id "friend-main-panel"

    :north (create-search-panel)
    :center (create-body-panel)

    :vgap 10
    :border 11))

(defn show-main-panel [view]
  (seesaw-core/show-card! (friend-utils/find-friend-panel view) main-panel-key)
  view)

(defn show-send-friend-request-panel [view]
  (seesaw-core/show-card! (friend-utils/find-friend-panel view)
                          send-friend-request-panel-key)
  view)

(defn create [friend-panel]
  (let [friend-panel-view
        (seesaw-core/card-panel
          :id friend-utils/card-panel-id
          :items [[(create-main-panel) main-panel-key]
                  [(send-friend-request-panel/create)
                    send-friend-request-panel-key]])]
    (friend-utils/save-panel friend-panel-view friend-panel)
    (show-main-panel friend-panel-view)))

(defn find-export-masque-button
  "Finds the export masque button in the given view."
  [view]
  (view-utils/find-component view export-masque-button-id))

(defn find-send-friend-request-button
  "Finds the send friend request button in the given view."
  [view]
  (view-utils/find-component view send-friend-request-button-id))

(defn find-all-friends-table
  "Finds the all friends table in the given view."
  [view]
  (view-utils/find-component view all-friends-table-id))

(defn find-my-requests-table
  "Finds the my requests table in the given view."
  [view]
  (view-utils/find-component view my-requests-table-id))

(defn find-sent-requests-table
  "Finds the sent requests table in the given view."
  [view]
  (view-utils/find-component view sent-requests-table-id))

(defn add-action-listener-to-export-masque-button
  "Adds the given action listener to the export mid button."
  [view listener]
  (view-utils/add-action-listener-to-button
    (find-export-masque-button view) listener export-masque-button-listener-key))

(defn add-extension-if-missing
  "Addes the .masque extension if it is missing to the given file."
  [file]
  (File. (.getParentFile file)
         (string-utils/add-ending-if-absent (.getName file) ".masque")))

(defn export-masque-success
  [file-chooser masque-file]
  (let [masque-file (add-extension-if-missing masque-file)]
    (if (.exists masque-file)
      (when (= (term/overwrite)
               (seesaw-core/input (term/file-already-exists-overwrite)
                                  :choices [(term/overwrite)
                                            (term/do-not-overwrite)]))
        (profile-model/create-masque-file masque-file))
      (profile-model/create-masque-file masque-file))))

(defn export-masque-listener
  "The export mid listener which exports the masques id to a directory the user
chooses."
  [event]
  (view-utils/save-file (seesaw-core/to-widget event) export-masque-success
                        friend-utils/masque-file-filters))

(defn attach-export-masque-listener
  "Attaches the export mid listener to the export masque button in the given view."
  [view]
  (add-action-listener-to-export-masque-button view export-masque-listener))

(defn add-action-listener-to-send-friend-request-button
  "Adds the given action listener to the send friend request button."
  [view listener]
  (view-utils/add-action-listener-to-button
    (find-send-friend-request-button view) listener
    send-friend-request-button-listener-key))

(defn send-friend-request-listener
  "Opens the import friend panel."
  [event]
  (show-send-friend-request-panel
    (friend-utils/find-friend-panel (view-utils/top-level-ancestor
                         (seesaw-core/to-widget event)))))

(defn attach-send-friend-request-listener
  "Attaches the send friend request listener to the send friend request button
in the given view."
  [view]
  (add-action-listener-to-send-friend-request-button view
                                              send-friend-request-listener))

(defn initialize
  "Called when the panel is created to initialize the view by attaching
listeners and loading initial data."
  [view show-panel-fn]
  (friend-utils/save-show-panel-fn view show-panel-fn)
  (send-friend-request-panel/initialize view)
  (attach-export-masque-listener view)
  (attach-send-friend-request-listener view))

(defn show
  "Makes sure the main panel is visible."
  [view args]
  (show-main-panel view))

(defn destroy-all-friends-table
  "Unregisters all of the listeners for the all friends table."
  [view]
  (korma-table-model/destroy-model (find-all-friends-table view)))

(defn destroy-my-requests-table
  "Unregisters all of the listeners for the my requests table."
  [view]
  (korma-table-model/destroy-model (find-my-requests-table view)))

(defn destroy-sent-requests-table
  "Unregisters all of the listeners for the sent requests table."
  [view]
  (korma-table-model/destroy-model (find-sent-requests-table view)))

(defn destroy
  "Cleans up anything that needs to be cleaned up when the panel is destroyed."
  [view]
  (destroy-all-friends-table view)
  (destroy-my-requests-table view)
  (destroy-sent-requests-table view))