package com.example.testcamerax

import com.google.gson.annotations.SerializedName

data class DistanceMatrixResponse(

	@SerializedName("destination_addresses")
	val destinationAddresses: List<String?>? = null,

	@SerializedName("rows")
	val rows: List<RowsItem?>? = null,

	@SerializedName("origin_addresses")
	val originAddresses: List<String?>? = null,

	@SerializedName("status")
	val status: String? = null
)

data class RowsItem(

	@SerializedName("elements")
	val elements: List<ElementsItem?>? = null
)

data class Duration(

	@SerializedName("text")
	val text: String? = null,

	@SerializedName("value")
	val value: Int? = null
)

data class ElementsItem(

	@SerializedName("duration")
	val duration: Duration? = null,

	@SerializedName("distance")
	val distance: Distance? = null,

	@SerializedName("status")
	val status: String? = null
)

data class Distance(

	@SerializedName("text")
	val text: String? = null,

	@SerializedName("value")
	val value: Int? = null
)
