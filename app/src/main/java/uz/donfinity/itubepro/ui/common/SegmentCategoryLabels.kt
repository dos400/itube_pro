package uz.donfinity.itubepro.ui.common

import androidx.annotation.StringRes
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.SegmentCategory

/** Display name of a SponsorBlock category. */
@StringRes
fun SegmentCategory.labelRes(): Int = when (this) {
    SegmentCategory.SPONSOR -> R.string.sb_category_sponsor
    SegmentCategory.SELF_PROMO -> R.string.sb_category_selfpromo
    SegmentCategory.INTERACTION -> R.string.sb_category_interaction
    SegmentCategory.INTRO -> R.string.sb_category_intro
    SegmentCategory.OUTRO -> R.string.sb_category_outro
    SegmentCategory.PREVIEW -> R.string.sb_category_preview
    SegmentCategory.MUSIC_OFFTOPIC -> R.string.sb_category_music_offtopic
    SegmentCategory.FILLER -> R.string.sb_category_filler
}
