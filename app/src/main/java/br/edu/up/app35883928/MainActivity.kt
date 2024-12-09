package br.edu.up.app35883928

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.edu.up.app35883928.ui.theme.App35883928Theme
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Data class para armazenar os dados que queremos exibir
data class Entry(val id: String, val content: String)

class MainActivity : ComponentActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val firestore = FirebaseFirestore.getInstance() // Instância do Firestore
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicializa o Firebase Analytics
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        // Log de evento de inicialização
        logFirebaseEvent()

        setContent {
            App35883928Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScreenContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    // Função para logar um evento no Firebase Analytics
    private fun logFirebaseEvent() {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "main_activity")
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Main Activity Started")
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
    }

    @Composable
    fun ScreenContent(modifier: Modifier = Modifier) {
        var userInput by remember { mutableStateOf("") }
        var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
        var editingEntry by remember { mutableStateOf<Entry?>(null) }

        // Função para carregar os dados do Firestore e observar mudanças
        LaunchedEffect(Unit) {
            listenerRegistration = firestore.collection("entries")
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        Toast.makeText(this@MainActivity, "Erro ao carregar dados: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        // Atualiza a lista completamente com base nos documentos do snapshot
                        entries = snapshot.documents.map { document ->
                            Entry(
                                id = document.id,
                                content = document.getString("content") ?: "Sem conteúdo"
                            )
                        }
                    }
                }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Campo de texto para digitar ou editar um dado
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("Digite algo") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Botão para salvar ou atualizar no Firestore com cores personalizadas
            Button(
                onClick = {
                    if (userInput.isNotEmpty()) {
                        if (editingEntry != null) {
                            // Atualiza o dado existente no Firestore
                            updateInFirestore(editingEntry!!, userInput)
                        } else {
                            // Salva um novo dado no Firestore
                            saveToFirestore(userInput)
                        }
                        userInput = "" // Limpa o campo de texto
                        editingEntry = null // Limpa o estado de edição
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue, // Cor do fundo do botão
                    contentColor = Color.White // Cor do texto
                )
            ) {
                Text(if (editingEntry != null) "Atualizar no Firestore" else "Salvar no Firestore")
            }

            // Exibe os dados salvos no Firestore
            if (entries.isNotEmpty()) {
                Text("Dados armazenados no Firestore:", style = MaterialTheme.typography.titleLarge)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(entries) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = entry.content, modifier = Modifier.weight(1f))
                                Row {
                                    // Botão para editar o dado com cor personalizada
                                    Button(
                                        onClick = {
                                            userInput = entry.content
                                            editingEntry = entry // Marca o item como sendo editado
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Green, // Cor do fundo do botão
                                            contentColor = Color.White // Cor do texto
                                        )
                                    ) {
                                        Text("Editar")
                                    }
                                    // Botão para excluir o dado com cor personalizada
                                    Button(
                                        onClick = {
                                            deleteFromFirestore(entry.id)
                                        },
                                        modifier = Modifier.padding(start = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Red, // Cor do fundo do botão
                                            contentColor = Color.White // Cor do texto
                                        )
                                    ) {
                                        Text("Excluir")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Nenhum dado encontrado.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    // Função para salvar dados no Firestore
    private fun saveToFirestore(data: String) {
        val entry = hashMapOf(
            "content" to data,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("entries")
            .add(entry)
            .addOnSuccessListener {
                Toast.makeText(this, "Dados salvos com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao salvar os dados: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Função para atualizar dados no Firestore
    private fun updateInFirestore(entry: Entry, newContent: String) {
        firestore.collection("entries").document(entry.id)
            .update("content", newContent)
            .addOnSuccessListener {
                Toast.makeText(this, "Dados atualizados com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao atualizar os dados: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Função para excluir dados do Firestore
    private fun deleteFromFirestore(documentId: String) {
        firestore.collection("entries").document(documentId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Dados excluídos com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao excluir dados: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove() // Remover o listener quando a Activity for destruída
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    App35883928Theme {
        Text("Hello Android!")
    }
}
