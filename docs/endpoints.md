# Boti API endpoints (extracted from the official app bundle)

Source: `assets/public/*.js` of `com.botieducation.greenwood` 2.4.14.
100 app endpoints. The endpoint name is the 2nd argument of the ApiService
`get()`/`post()` calls; full URL =
`https://boti.education/p/greenwood/botiapi/<endpoint>`.

Excluded as pdf.js library false positives (all seen only in chunk 7482.js):
BPC, CS, DP, F, FontFile2, H, I, IM, SM, W

| endpoint | methods | chunk(s) |
|---|---|---|
| absences | get | 3058.js, 8317.js |
| absences-justification | post | 3058.js, 8317.js |
| acces_check | get | main.js |
| admin_absence_eleve | get | 6763.js |
| admin_demande_eleve | get, post | 1514.js, 9714.js, 9964.js |
| admin_discipline_eleve | get, post | 1172.js, 9964.js |
| admin_fiche_eleve | get | 9964.js |
| admin_nouveautes | get | 6154.js |
| admin_search_eleve | get | 6996.js |
| admin_suivi_eleve | get, post | 3555.js, 9964.js |
| album_photo | get | 6124.js, 6925.js |
| bibliotheque | get | 2662.js, 6587.js, 7856.js (+1 more) |
| cantine | get | 3218.js, 6510.js |
| cartable_numeriques | get | 1516.js, 8698.js, 9661.js |
| cartable_split | get | 3238.js |
| change-password | get, post | 5994.js |
| collaborateur_menu | get | 9246.js |
| collaborateur_nouveautes | get | 4450.js |
| competences | get | 219.js, 5502.js |
| compte | get | 2319.js, 7983.js, 8437.js |
| compte-eleve | get, post | 4186.js, 6943.js |
| compte-parent | get, post | 6941.js |
| compte-pick | get, post | 2758.js, 2881.js |
| compte-prof | get, post | 7324.js |
| contact | get | 112.js, 8171.js |
| cours_v2 | get | 6109.js, 6946.js |
| demandes | get | 1246.js |
| device_token | post | main.js |
| devoirs | get, post | 154.js, 3537.js, 4273.js (+1 more) |
| devoirs_date_v2 | post | 3537.js, 4273.js |
| discipline | get | 5482.js, 8072.js |
| encadrant_contact | get | 5962.js |
| encadrant_menu | get | 7110.js |
| encadrant_notifications | get | 6108.js |
| evaluations_releves | get, post | 1213.js, 6153.js |
| examen_collectif | get | 1531.js |
| examens_v2 | get | 2722.js, 7085.js |
| forgot-password | post | 1028.js |
| grille_apprentisage | get | 541.js |
| icon-top | get | 438.js |
| ios | get | 2175.js, 5817.js, 8136.js (+2 more) |
| login | post | main.js |
| login-page | get | 1028.js, 274.js, 4287.js |
| logout | post | main.js |
| messages | get | 1201.js, 3943.js, 4785.js (+1 more) |
| new_compte | post | 8437.js |
| new_compte/child | post | 8437.js |
| new_paiements | get | 4110.js |
| note_matieres | get | 215.js |
| notifications | get | 9963.js |
| notifications_new | get | 8052.js |
| nouveau-message | post | 1201.js, 2345.js, 9771.js |
| nouveautes | get, post | 1815.js, 1878.js, 1901.js (+8 more) |
| nouvelle-demande | get, post | 2253.js, 8302.js |
| objects | get, post | 7983.js |
| online_paiements | get | 4924.js |
| overlay | get | 8136.js |
| paiements | get, post | 1559.js, 1631.js, 2897.js (+4 more) |
| pick_enfants | get, post | 4495.js, 6292.js |
| pick_menu | get | 8897.js |
| pick_nouveautes | get | 9821.js |
| pick_trajets_tst_absent | get, post | 2568.js, 7347.js |
| pinned_posts | get | 8853.js, 9010.js |
| pointage | get, post | 3394.js, 8656.js |
| pointage_borne | get, post | 3151.js, 9073.js |
| post_view | get | 1815.js, 1878.js, 6834.js (+2 more) |
| prof-competences | get | 5507.js, common.js |
| prof-competences-details | get, post | 1272.js |
| prof-nouvelle-demande | get, post | 718.js |
| prof_cachier_text | get, post | 6919.js |
| prof_cartable_numeriques | get | 2891.js, 3358.js |
| prof_classes | get, post | 7116.js, 9516.js |
| prof_competence_form | get, post | 4225.js |
| prof_content | get, post | 4225.js, 6516.js, 6904.js (+2 more) |
| prof_contents | get | 7269.js, 9346.js, 9588.js |
| prof_cours | get | 5814.js |
| prof_cours_details | get, post | 8111.js |
| prof_demandes | get, post | 1665.js, 8329.js |
| prof_devoir_details | get | 6904.js, 950.js |
| prof_examen_form | get, post | 103.js |
| prof_examens_details | get, post | 1531.js, 5908.js |
| prof_fiche_eleve | get | 6889.js |
| prof_generale_appreciation | get, post | 2567.js, 712.js |
| prof_media_album | get, post | 322.js, 7859.js, 8667.js |
| prof_messages | get, post | 3733.js, 4722.js, 8690.js |
| prof_notifications | get | 3602.js |
| prof_nouveautes | get | 6657.js |
| prof_ressource_partage | get, post | 1267.js, 2984.js, 8638.js |
| prof_transmission | get, post | 2629.js |
| quiz | get, post | 1140.js, 93.js |
| ressource_details | get | 2921.js, 7424.js |
| ressources_v2 | get, post | 3641.js, 4655.js |
| shop | get, post | 1097.js, 6091.js, 9344.js (+1 more) |
| slides | get, post | 1356.js |
| slides_content | get | main.js |
| student-wallet | get, post | 2699.js, 333.js, 6277.js (+1 more) |
| suivi_pedagogique | get | 1414.js, 3965.js |
| timeline | get | 274.js, 4287.js |
| translations | get | 2757.js, 4674.js, 6113.js (+3 more) |
| transmission | get | 2109.js, 63.js |
