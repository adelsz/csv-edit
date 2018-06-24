(ns csv-app.core
  (:require
   [rum.core :as rum]
   [clojure.reader :as reader]
   [clojure.string :as str]))

(enable-console-print!)

(defn validate [table]
  (let [error (cond ; one could add more assertions here, but lets not overengineer it
                (< (count table) 2)              "data must have at least two rows"
                (some #(not= (count %) 2) table) "data must have exactly two columns"
                :else                            nil)]
    {:is-success (nil? error) :error error}))

(defn parse [content on-done]
  (let [lines (str/split content "\n")
        table (mapv
               (fn [line] (str/split line ","))
               lines)]
    (on-done (validate table) table)))

(defn handle-file [e on-done]
  (let [files (.. e -target -files)]
    (if (> (.-length files) 0)
        (let [file (aget files 0)
                reader (new js/FileReader)]
            (set! (.-onloadend reader) (fn [] (parse (.-result reader) on-done)))
            (.readAsText reader file)))))

(defn table-sum [table]
  (reduce (fn [x [_ y]] (+ x (reader/read-string y))) 0 table))

(defn table-average [table]
  (/ (table-sum table) (count table)))

(rum/defc aggregation < rum/static
  [sum mean]
  [:table.table.table-bordered.table-dark
      [:thead
       [:tr.bg-info [:th.text-center {:col-span 2} "Aggregation"]]
       [:tr [:th "Sum"] [:th "Average"]]]
      [:tbody
       [:tr
        [:td sum]
        [:td mean]]]])

(def table-atom (atom []))

(rum/defcs editable-cell <
  rum/reactive
  (rum/local false ::is-editing)
  [state tag content-atom]
  (let [is-editing (::is-editing state)]
    [tag 
     {:on-click #(reset! is-editing true)}
     (if @is-editing
       [:input.form-control
        {:type "text"
         :auto-focus true
         :value @content-atom 
         :on-blur #(reset! is-editing false)
         :on-change (fn [e]
                        (let [value  (.. e -target -value)]
                        (reset! content-atom value)))
         :on-key-down (fn [e] (if (= (. e -key ) "Enter") (reset! is-editing false)))
         }]
       [:span (str (rum/react content-atom))])]
    ))

(defn wrap-cells
  [section-tag cell-tag row-range col-range]
  [section-tag
   (for [i row-range]
          [:tr {:key i}
            (for [j col-range]
              (rum/with-key
                (editable-cell cell-tag (rum/cursor-in table-atom [i j]))
                j ))])])


(rum/defcs main <
  rum/reactive
  (rum/local false ::has-data)
  (rum/local nil ::error)
  [state label]
  (let [has-data-atom (::has-data state)
        error-atom (::error state)
        on-done (fn [validation table]
                  (if (:is-success validation)
                    (do
                        (reset! has-data-atom true)
                        (reset! error-atom nil)
                        (reset! table-atom table))
                    (reset! error-atom (:error validation))))
        table-body (rest (rum/react table-atom))]
    [:div.container
     [:h1 label]
     (if (nil? @error-atom)
       (if @has-data-atom
         [[:table.table.table-bordered.table-dark
           (wrap-cells :thead.bg-primary :th [0] [0 1])
           (wrap-cells :tbody :td (range 1 (+ 1 (count table-body))) [0 1])]
          (aggregation (table-sum table-body) (table-average table-body))]
         [:div.alert.alert-info "No data to show. Please select a CSV file first."])
       [:div.alert.alert-warning (str "Error: " @error-atom)]
       )
     [:input {:type "file"
              :id "input"
              :on-change #(handle-file % on-done)}]]))

(defn render []
  (rum/mount (main "CSV editor") (. js/document (getElementById "app"))))
