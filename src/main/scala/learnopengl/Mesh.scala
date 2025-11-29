package learnopengl.mesh

import learnopengl.shader.Shader
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

val MAX_BONE_INFLUENCE = 4

case class Vertex(
    // position
    Position: Vector3f,
    // normal
    Normal: Vector3f,
    // texCoords
    TexCoords: Vector2f,
    // tangent
    Tangent: Vector3f,
    // bitangent
    Bitangent: Vector3f,
    // bone indexes which will influence this vertex
    m_BoneIDs: Seq[Int] = Seq.fill(MAX_BONE_INFLUENCE)(0),
    // weights from each bone
    m_Weights: Seq[Float] = Seq.fill(MAX_BONE_INFLUENCE)(0f)
)

case class Texture(id: Int, tpe: String, path: String)

class Mesh(
    vertices: Seq[Vertex],
    indices: Seq[Int],
    textures: Seq[Texture]
):
  // create buffers/arrays
  private val vao: Int = glGenVertexArrays()
  private val vbo = glGenBuffers()
  private val ebo = glGenBuffers()

  setupMesh()

  def draw(shader: Shader): Unit =
    var diffuseNr = 1
    var specularNr = 1
    var normalNr = 1
    var heightNr = 1

    for (texture, i) <- textures.zipWithIndex do
      // active proper texture unit before binding
      glActiveTexture(GL_TEXTURE0 + i)

      val number = texture.tpe match
        case "texture_diffuse"  => diffuseNr += 1; diffuseNr - 1
        case "texture_specular" => specularNr += 1; specularNr - 1
        case "texture_normal"   => normalNr += 1; normalNr - 1
        case "texture_height"   => heightNr += 1; heightNr - 1
        case _                  => 1

      // now set the sampler to the correct texture unit
      shader.setInt(s"${texture.tpe}$number", i)
      glBindTexture(GL_TEXTURE_2D, texture.id)

    // draw mesh
    glBindVertexArray(vao)
    glDrawElements(GL_TRIANGLES, indices.length, GL_UNSIGNED_INT, 0)
    glBindVertexArray(0)

    // always good practice to set everything back to defaults once configured.
    glActiveTexture(GL_TEXTURE0)

  // initializes all the buffer objects/arrays
  private def setupMesh(): Unit =
    glBindVertexArray(vao)
    // load data into vertex buffers
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    // 4 * vec3 + 1 * vec2 = 14 floats
    val stride = (4 * 3 + 2 + 2 * MAX_BONE_INFLUENCE) * 4

    //
    val buffer = java.nio.ByteBuffer
      .allocateDirect(vertices.length * stride)
      .order(java.nio.ByteOrder.nativeOrder())

    vertices.foreach { v =>
      buffer.putFloat(v.Position.x)
      buffer.putFloat(v.Position.y)
      buffer.putFloat(v.Position.z)

      buffer.putFloat(v.Normal.x)
      buffer.putFloat(v.Normal.y)
      buffer.putFloat(v.Normal.z)

      buffer.putFloat(v.TexCoords.x)
      buffer.putFloat(v.TexCoords.y)

      buffer.putFloat(v.Tangent.x)
      buffer.putFloat(v.Tangent.y)
      buffer.putFloat(v.Tangent.z)

      buffer.putFloat(v.Bitangent.x)
      buffer.putFloat(v.Bitangent.y)
      buffer.putFloat(v.Bitangent.z)

      v.m_BoneIDs.foreach(buffer.putInt)
      v.m_Weights.foreach(buffer.putFloat)
    }

    buffer.flip()
    glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.toArray, GL_STATIC_DRAW)

    // set the vertex attribute pointers
    // vertex Positions
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0)

    // vertex normals
    glEnableVertexAttribArray(1)
    glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * 4)

    // vertex texture coords
    glEnableVertexAttribArray(2)
    glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * 4)

    // vertex tangent
    glEnableVertexAttribArray(3)
    glVertexAttribPointer(3, 3, GL_FLOAT, false, stride, 8 * 4)

    // vertex bitangent
    glEnableVertexAttribArray(4)
    glVertexAttribPointer(4, 3, GL_FLOAT, false, stride, 11 * 4)

    // ids
    glEnableVertexAttribArray(5)
    glVertexAttribIPointer(5, MAX_BONE_INFLUENCE, GL_INT, stride, 14 * 4L)

    // weights
    glEnableVertexAttribArray(6)
    glVertexAttribPointer(
      6,
      MAX_BONE_INFLUENCE,
      GL_FLOAT,
      false,
      stride,
      (14 + MAX_BONE_INFLUENCE) * 4L
    )
    glBindVertexArray(0)
