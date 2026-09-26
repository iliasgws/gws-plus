package school.greenwood.plus.data.api

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap
import school.greenwood.plus.data.session.SessionStore
import java.util.concurrent.TimeUnit

/*
 * Le serveur Boti expose chaque fonctionnalité sous une route plate : le nom
 * du point d'accès EST la route (pas de /api, pas de .php). Plutôt que cent
 * interfaces miroirs, deux méthodes génériques : un GET à params, un POST
 * multipart. Les dépôts (data/repo) nomment les endpoints et normalisent.
 */

interface BotiApi {
    @GET("{endpoint}")
    suspend fun get(
        @Path("endpoint", encoded = true) endpoint: String,
        @QueryMap params: Map<String, String>,
    ): ResponseBody

    @POST("{endpoint}")
    suspend fun post(
        @Path("endpoint", encoded = true) endpoint: String,
        @Body body: RequestBody,
    ): ResponseBody
}

object BotiHttp {

    /** UA éprouvé par le client de référence ; le serveur grogne sur les inconnues. */
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(req)
        }
        .build()

    fun api(client: OkHttpClient = client()): BotiApi = Retrofit.Builder()
        .baseUrl("https://boti.education/p/greenwood/botiapi/")
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(BotiApi::class.java)
}
