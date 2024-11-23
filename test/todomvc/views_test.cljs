(ns todomvc.views-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [lookup.core :as l]
   [todomvc.actions :as a]
   [todomvc.views :as sut]
   [clojure.string :as string]))

#_{:clj-kondo/ignore [:private-call]}
(deftest maybe-add
  (testing "it should add a new item when the string is not blank"
    (let [result (sut/maybe-add [] "New item")]
      (is (= 1 (count result)))
      (is (= "New item" (:item/title (first result))))
      (is (false? (:item/completed (first result))))
      (is (uuid? (:item/id (first result))))))

  (testing "it should not add a new item when the string is empty"
    (is (= 0 (count (sut/maybe-add [] "")))))

  (testing "it should not add a new item when the string is blank"
    (is (= 0 (count (sut/maybe-add [] "   ")))))

  (testing "it should trim the string before adding a new item"
    (is (= "New item" (-> (sut/maybe-add [] "  New item  ")
                          first
                          :item/title))))

  (testing "items are added to the end of the list"
    (is (= "Second item" (-> (sut/maybe-add [{:item/title "First item"}] "Second item")
                             second
                             :item/title)))))

(defn- select-attribute
  [selector path data]
  (let [elements (l/select selector data)]
    (->> (keep (fn [element]
                 (when (map? (second element))
                   (get-in (second element) path)))
               elements))))

(defn- flatten-actions [actionss]
  (reduce into [] actionss))

(defn- select-actions [selector path data]
  (->> (select-attribute selector path data)
       flatten-actions))

#_{:clj-kondo/ignore [:private-call]}
(defn test-add-view-mount [state]
  #_{:clj-kondo/ignore [:inline-def :clojure-lsp/unused-public-var]}
  (comment
    (def state {}))
  (let [on-mount-actions (->> (sut/add-view state)
                              (select-actions :input.new-todo [:replicant/on-mount]))
        {:keys [new-state effects] :as result} (a/handle-actions state
                                                                 {:replicant/node :input-dom-node}
                                                                 on-mount-actions)]
    (is (= :input-dom-node
           (:add/draft-input-element new-state)))
    (is (empty? effects)
        "it does so without other side-effects")
    result))

#_{:clj-kondo/ignore [:private-call]}
(defn test-add-view-input [state add-text]
  #_{:clj-kondo/ignore [:inline-def :clojure-lsp/unused-public-var]}
  (comment
    (def state {:add/draft-input-element :input-dom-node})
    (def add-text "Input"))
  (let [on-input-actions (->> (sut/add-view state)
                              (select-actions :input.new-todo [:on :input]))
        {:keys [new-state effects] :as result} (a/handle-actions state
                                                                 {:replicant/js-event (clj->js {:target {:value add-text}})}
                                                                 on-input-actions)]
    (is (= add-text
           (:add/draft new-state)))
    (is (empty? effects)
        "it does so without other side-effects")
    result))

#_{:clj-kondo/ignore [:private-call]}
(deftest add-view
  (testing "it saves draft input element on mount"
    (let [{:keys [new-state]} (test-add-view-mount {})]

      (testing "it updates the draft from the `.new-todo` input event"
        (let [input-text "Input"
              {:keys [new-state]} (test-add-view-input new-state input-text)]

          (testing "it handles the form submit event"
            (let [on-submit-actions (->> (sut/add-view new-state)
                                         (select-actions :form [:on :submit]))
                  {:keys [new-state effects]} (a/handle-actions new-state
                                                                {}
                                                                on-submit-actions)]
              (is (= input-text
                     (-> new-state :app/todo-items first :item/title))
                  "it adds the new item to the todo items")
              (is (= ""
                     (:add/draft new-state))
                  "it clears the draft")
              (is (some #{[:dom/fx.prevent-default]}
                        (set effects))
                  "it prevents the default form submit action")
              (is (some #{[:dom/fx.set-input-text :input-dom-node ""]}
                        (set effects))
                  "it clears the input element")))))

      (testing "it trims the input"
        (let [input-text "Input"
              untrimmed-test (str "  " input-text "  ")
              {:keys [new-state]} (test-add-view-input new-state untrimmed-test)]

          (testing "it handles the form submit event"
            (let [on-submit-actions (->> (sut/add-view new-state)
                                         (select-actions :form [:on :submit]))
                  {:keys [new-state]} (a/handle-actions new-state
                                                        {}
                                                        on-submit-actions)]
              (is (= input-text
                     (-> new-state :app/todo-items first :item/title))
                  "it adds the new item with trimmed text to the todo items")))))

      (testing "it doesn't add an item when the input is empty"
        (let [input-text ""
              {:keys [new-state]} (test-add-view-input new-state input-text)]

          (testing "it handles the form submit event"
            (let [on-submit-actions (->> (sut/add-view new-state)
                                         (select-actions :form [:on :submit]))
                  {:keys [new-state effects]} (a/handle-actions new-state
                                                                {}
                                                                on-submit-actions)]
              (is (empty? (:app/todo-items new-state))
                  "it does not add a new item to the todo items")
              (is (= ""
                     (:add/draft new-state))
                  "it clears the draft")
              (is (some #{[:dom/fx.prevent-default]}
                        (set effects))
                  "it prevents the default form submit action")
              (is (some #{[:dom/fx.set-input-text :input-dom-node ""]}
                        (set effects))
                  "the input element remains blank"))))))))

#_{:clj-kondo/ignore [:private-call]}
(deftest edit-view
  (testing "rendering the edit view"
    (let [initial-state {:edit/editing-item-index 0}
          edit-view (sut/edit-view initial-state 0 {})]
      (is (l/select :input.edit edit-view)
          "it renders the edit view when the index matches the editing item index"))

    (let [initial-state {}
          edit-view (sut/edit-view initial-state 0 {})]
      (is (nil? edit-view)
          "it does not render the edit view when its index does not match the editing item index"))

    (let [initial-state  {:edit/editing-item-index 0
                          :edit/keyup-code "Escape"}
          edit-view (sut/edit-view initial-state 0 {})]
      (is (nil? edit-view)
          "it does not render the edit view when the keycode is 'Escape'")))

  (testing "edit-view on mount"
    (let [initial-state {:edit/editing-item-index 0}
          on-mount-actions (->> (sut/edit-view initial-state 0 {})
                                (select-actions :input.edit [:replicant/on-mount]))
          {:keys [new-state effects]} (a/handle-actions initial-state
                                                        {:replicant/node :input-dom-node}
                                                        on-mount-actions)]
      (is (some #{[:dom/fx.focus-element :input-dom-node]}
                (set effects))
          "it focuses the input element")
      (is (= initial-state
             new-state)
          "it does not modify the state")))

  (testing "it populates the edit view from the edited item"
    (let [item {:item/title "Title"}
          initial-state {:edit/editing-item-index 0}
          edit-view (sut/edit-view initial-state 0 item)]
      (is (= [(:item/title item)]
             (select-attribute :input.edit [:value] edit-view))
          "it populates the input with the item title")))

  (testing "it updates the draft from the input event"
    (let [initial-state {:edit/editing-item-index 0}
          on-input-actions (->> (sut/edit-view initial-state 0 {})
                                (select-actions :input.edit [:on :input]))
          {:keys [new-state effects]} (a/handle-actions initial-state
                                                        {:replicant/js-event (clj->js {:target {:value "Input"}})}
                                                        on-input-actions)]
      (is (= "Input"
             (:edit/draft new-state))
          "it updates the draft")
      (is (empty? effects)
          "it does so without other side-effects")))

  (testing "it saves the keycode to the state on keyup"
    (let [initial-state {:edit/editing-item-index 0}
          on-keyup-actions (->> (sut/edit-view initial-state 0 {})
                                (select-actions :input.edit [:on :keyup]))
          {:keys [new-state effects]} (a/handle-actions initial-state
                                                        {:replicant/js-event (clj->js {:code "Escape"})}
                                                        on-keyup-actions)]
      (is (= "Escape"
             (:edit/keyup-code new-state))
          "it saves the keycode")
      (is (empty? effects)
          "it does so without other side-effects")))

  (testing "it removes the editing index on blur"
    (let [initial-state {:edit/editing-item-index 0}
          on-blur-actions (->> (sut/edit-view initial-state 0 {})
                               (select-actions :input.edit [:on :blur]))
          {:keys [new-state effects]} (a/handle-actions initial-state
                                                        {}
                                                        on-blur-actions)]
      on-blur-actions
      (is (nil?
           (:edit/editing-item-index new-state))
          "it removes the editing index")
      (is (empty? effects)
          "it does so without other side-effects")))

  (testing "it removes the editing index on form submit"
    (let [initial-state {:edit/editing-item-index 0}
          on-submit-actions (->> (sut/edit-view initial-state 0 {})
                                 (select-actions :form [:on :submit]))
          {:keys [new-state effects]} (a/handle-actions initial-state
                                                        {}
                                                        on-submit-actions)]
      (is (nil?
           (:edit/editing-item-index new-state))
          "it removes the editing index")
      (is (some #{[:dom/fx.prevent-default]}
                (set effects))
          "it prevents the default form submission")))

  (testing "end editing"
    (doseq [[input behaviour] [["Input" "it updates the item title"]
                               ["  Input  " "it trims the input"]]]
      (let [item {:item/title "Title"}
            initial-state {:edit/editing-item-index 0
                           :edit/draft input
                           :app/todo-items [item]}
            on-unmount-actions (->> (sut/edit-view initial-state 0 item)
                                    (select-actions :form [:replicant/on-unmount]))
            {:keys [new-state effects]} (a/handle-actions initial-state
                                                          {}
                                                          on-unmount-actions)]
        (is (= (string/trim input)
               (-> new-state :app/todo-items first :item/title))
            behaviour)
        (is (nil? (:edit/editing-item-index new-state))
            "it removes the editing index")
        (is (nil? (:edit/keyup-code new-state))
            "it removes the keycode")
        (is (empty? effects)
            "it does not have other side-effects")))

    (doseq [[input input-behaviour] [["" "it removes the item if the input is empty"]
                                     ["  " "it removes the item if the trimmed input is empty"]]
            completed-item {:item/title "Title2"
                            :item/completed true}
            uncompleted-item {:item/title "Title2"
                              :item/completed false}
            [items mark-all-state items-behaviour] [[[{:item/title "Title1"
                                                       :item/completed false}
                                                      completed-item
                                                      {:item/title "Title3"
                                                       :item/completed false}]
                                                     false
                                                     "it sets the mark-all state to false if the remaining items are uncompleted"]
                                                    [[{:item/title "Title1"
                                                       :item/completed true}
                                                      uncompleted-item
                                                      {:item/title "Title3"
                                                       :item/completed true}]
                                                     true
                                                     "it sets the mark-all state to true if the remaining items are completed"]]]
      (let [initial-state {:edit/editing-item-index 1
                           :edit/keyup-code "Enter"
                           :edit/draft input
                           :app/todo-items items}
            on-unmount-actions (->> (sut/edit-view initial-state 1 (second items))
                                    (select-actions :form [:replicant/on-unmount]))
            {:keys [new-state effects]} (a/handle-actions initial-state
                                                          {}
                                                          on-unmount-actions)]
        (is (= [(first items) (last items)]
               (:app/todo-items new-state))
            input-behaviour)
        (is (nil? (:edit/editing-item-index new-state))
            "it removes the editing index")
        (is (nil? (:edit/keyup-code new-state))
            "it removes the keycode")
        (is (empty? effects)
            "it does not have other side-effects")
        (is (= mark-all-state
               (:app/mark-all-state new-state))
            items-behaviour)))

    (testing "It does not update the item when Escape has been pressed"
      ; Notes:
      ; :app/mark-all-state
      ;   * The initial states has no :app/mark-all-state key, even though in “reality” it would have
      ;     We do this so that we can test that its not updated
      ;
      ; :edit/keyup-code and the element lifecycle
      ;   * The state rendering the view had no `:edit/keyup-code "Escape"` entry,
      ;     hence the edit UI was rendered
      ;   * The state captured at unmounting the view _had_ an `:edit/keyup-code "Escape"` entry,
      ;     and this should cause the indexed todo item to be left unchanged
      (let [item {:item/title "Title2"
                  :item/completed true}
            initial-items [{:item/title "Title1"
                            :item/completed false}
                           item
                           {:item/title "Title3"
                            :item/completed false}]
            rendering-state {:edit/editing-item-index 1
                             :edit/keyup-code "KeyT" ; Doesn't matter, but if the "t" in "Input" was typed last...
                             :edit/draft "Input"
                             :app/todo-items initial-items}
            unmounting-state (assoc rendering-state :edit/keyup-code "Escape")
            on-unmount-actions (->> (sut/edit-view rendering-state 1 item)
                                    (select-actions :form [:replicant/on-unmount]))
            {:keys [new-state effects]} (a/handle-actions unmounting-state
                                                          {}
                                                          on-unmount-actions)]
        (is (= initial-items
               (:app/todo-items new-state))
            "it doesn't update any item")
        (is (nil? (:edit/editing-item-index new-state))
            "it removes the editing index")
        (is (nil? (:edit/keyup-code new-state))
            "it removes the keycode")
        (is (empty? effects)
            "it does not have other side-effects")
        (is (nil? (:app/mark-all-state new-state))
            "it does not update the mark-all state")))))

(deftest app-view
  (testing "it shows a `.todoapp` element"
    (is (seq (l/select '.todoapp (sut/app-view {})))))

  (testing "the `.todoapp` element contains a `.header` element with a h1 element with the text `todos`"
    (is (= '([:h1 "todos"])
           (l/select '[.todoapp .header h1] (sut/app-view {})))))

  (testing "the `.todoapp` element contains an autofocused `.new-todo` input in the `.header` element"
    (is (= '(true)
           (select-attribute '[.todoapp .header input.new-todo]
                             [:autofocus]
                             (sut/app-view {})))
        "with an empty app state")

    (is (= '(true)
           (select-attribute '[.todoapp .header input.new-todo]
                             [:autofocus]
                             (sut/app-view {:app/todo-items [{:item/title "First item"}]})))
        "with items in the app state"))

  (testing "the `.todoapp` element `.main` element"
    (is (empty? (l/select '[.todoapp .main] (sut/app-view {})))
        "it has no `.main` view when there are no items")
    (is (seq (l/select '[.todoapp .main] (sut/app-view {:app/todo-items [{:item/title "First item"}]})))
        "it has a `.main` view when there are items"))

  (testing "the `.todoapp` element `.footer` element"
    (is (empty? (l/select '[.todoapp .footer] (sut/app-view {})))
        "it has no `.footer` when there are no items")
    (is (seq (l/select '[.todoapp .footer] (sut/app-view {:app/todo-items [{:item/title "First item"}]})))
        "it has a `.footer` when there are items"))

  (testing "all form-submits have a prevent-default action"
    (is (every? (partial some #{[:dom/ax.prevent-default]})
                (->> (sut/app-view {:app/todo-items [{:item/title "First item"}]
                                    :edit/editing-item-index 0
                                    :app/item-filter :filter/all})
                     (select-attribute 'form [:on :submit])
                     (map set))))))