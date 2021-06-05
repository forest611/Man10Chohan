package red.man10.man10chohan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.security.SecureRandom.getInstance
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.floor

class Man10Chohan : JavaPlugin() {


    companion object{

        const val prefix = "§f§l[§d§lMa§f§ln§a§l10§c§l丁§b§l半§f§l]"
        var game : Game? = null

        private val es = Executors.newSingleThreadExecutor()

        lateinit var vaultManager: VaultManager


        fun format(double: Double):String{
            return String.format("%,.1f",double)
        }

        fun start(bet: Double){
            game = Game()
            game!!.bet = bet

            es.execute { game!!.betTimer() }

        }

        fun finish(){
            game = null
        }
    }

    private val minAmount = 10000.0
    private var isEnable = true

    override fun onEnable() {
        // Plugin startup logic

        vaultManager = VaultManager(this)
        server.pluginManager.registerEvents(Game(),this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {

        if (sender !is Player)return true

        if (args.isNullOrEmpty()){

            if (!sender.hasPermission("chohan.user")){ return true }

            sender.sendMessage("${prefix}§e§l------------§0§l[§d§lMa§f§ln§a§l10§c§l丁§b§l半§0§l]§e§l------------")
            sender.sendMessage("${prefix}§c/mc open <bet>§f:丁半を開<金額は${minAmount}以上>")
            sender.sendMessage("${prefix}§c/mc c§f:§c丁(偶数)に賭ける")
            sender.sendMessage("${prefix}§c/mc h§f:§b半(奇数)に賭ける")
            if (game != null){
                sender.sendMessage("""
                    §e§l----------------------------------
                    §c丁:${game!!.playerCho.size}人
                    §b半:${game!!.playerHan.size}人
                    §e合計Bet:${format(game!!.getTotal())}円
                """.trimIndent())

            }


            return true
        }

        when(args[0]){

            "open" ->{

                if (!isEnable)return true

                if (!sender.hasPermission("chohan.user")){ return true }

                if (game != null){
                    sender.sendMessage("${prefix}§cすでにゲームが開いています！")
                    return true
                }

                if (args.size < 2){
                    sender.sendMessage("${prefix}§c入力方法に誤りがあります！")
                    return true
                }

                val bet = args[1].toDoubleOrNull()

                if (bet==null || bet<=minAmount){
                    sender.sendMessage("${prefix}§6Bet金額に問題があります！")
                    return true
                }

                start(bet)
            }

            "c" ->{

                if (!sender.hasPermission("chohan.user")){ return true }

                if (game != null){
                    game!!.betCho(sender)
                    return true
                }
                sender.sendMessage("${prefix}§cゲームが開いていません！")

                return true
            }

            "h" ->{

                if (!sender.hasPermission("chohan.user")){ return true }

                if (game != null){
                    game!!.betHan(sender)
                    return true
                }
                sender.sendMessage("${prefix}§cゲームが開いていません！")

                return true
            }

            "on" ->{
                if (!sender.hasPermission("chohan.op")){ return true }

                isEnable = true
            }

            "off" ->{
                if (!sender.hasPermission("chohan.op")){ return true }

                isEnable = false
            }

        }


        return false
    }

    class Game : Listener{

        val playerCho = mutableListOf<Player>()
        val playerHan = mutableListOf<Player>()

        var bet: Double = 0.0
        var canJoin = true

        private val random = getInstance("NativePRNGNonBlocking")

        fun betCho(p:Player){

            if (!canJoin)return

            if (playerCho.contains(p)){
                p.sendMessage("${prefix}§c既に丁に賭けています！")
                return
            }

            if (playerHan.contains(p)){
                playerHan.remove(p)
                playerCho.add(p)
                p.sendMessage("${prefix}§c丁に移動しました！")
                return
            }

            if (vaultManager.withdraw(p.uniqueId,bet)){
                p.sendMessage("${prefix}§c丁に賭けました！")
                playerCho.add(p)
                return
            }

            p.sendMessage("${prefix}§c所持金が足りません！")

        }

        fun betHan(p:Player){

            if (!canJoin)return

            if (playerHan.contains(p)){
                p.sendMessage("${prefix}§b既に半に賭けています！")
                return
            }

            if (playerCho.contains(p)){
                playerCho.remove(p)
                playerHan.add(p)
                p.sendMessage("${prefix}§b半に移動しました！")
                return
            }

            if (vaultManager.withdraw(p.uniqueId,bet)){
                p.sendMessage("${prefix}§b半に賭けました！")
                playerHan.add(p)
                return
            }

            p.sendMessage("${prefix}§c所持金が足りません！")

        }

        fun quit(p:Player){
            if (playerHan.contains(p)){
                playerHan.remove(p)
                return
            }
            if (playerCho.contains(p)){
                playerCho.remove(p)
                return
            }
        }

        fun betTimer(){

            val textCho = Component.text("§c§l[丁(偶数)に賭ける]").clickEvent(ClickEvent.runCommand("/mc c"))
            val textHan = Component.text("§b§l[半(奇数)に賭ける]").clickEvent(ClickEvent.runCommand("/mc h"))

            Bukkit.broadcast(Component.text("${prefix}§6§l${format(bet)}円§a§l丁半が始められました!§f§l[/mc]"))
            Bukkit.broadcast(textCho.append(textHan))

            for (i in 3 downTo 1){
                Thread.sleep(10000)

                Bukkit.broadcast(Component.text("${prefix}§aBet受付終了まであと${i*10}秒"))
            }

            if (playerCho.size+playerHan.size <= 1 ){
                Bukkit.broadcast(Component.text("${prefix}§4§l人数が集まらなかったため中止しました"))
                refund()
                finish()
                return
            }

            canJoin = false

            sendGameUser("${prefix}§a§lサイコロを振っています…  §f§l§k123")

            Thread.sleep(3000)

            val result = random.nextInt(6) + 1

            val payout : Double

            val chouhan = if (result % 2 == 0){

                payout = floor(getTotal()/playerCho.size)

                playerCho.forEach { win(it,payout) }
                playerHan.forEach { lose(it) }

                "§c§l丁"
            }else{

                payout = floor(getTotal()/playerHan.size)

                playerCho.forEach { lose(it) }
                playerHan.forEach { win(it,payout) }

                "§b§l半"
            }

            sendGameUser("${prefix}§f結果: §e§l${result} ${chouhan}の勝利§c(支払額:${format(payout)}円 参加者:${playerCho.size+playerHan.size})")

            finish()

        }

        private fun win(p:Player,payout:Double){

            p.sendMessage("${prefix}§b§lあなたは勝ちました！")
            vaultManager.deposit(p.uniqueId,payout)
            Bukkit.getLogger().info("Chohan Win $payout ${p.name}")

        }

        private fun lose(p:Player){
            p.sendMessage("${prefix}§c§lあなたは負けました！")
        }

        private fun refund(){

            for (p in playerHan){
                vaultManager.deposit(p.uniqueId,bet)
            }

            for (p in playerCho){
                vaultManager.deposit(p.uniqueId,bet)
            }

        }

        private fun sendGameUser(msg:String){
            playerHan.forEach { it.sendMessage(msg) }
            playerCho.forEach { it.sendMessage(msg) }
        }

        fun getTotal():Double{
            return (playerCho.size+playerHan.size)*bet
        }

        @EventHandler
        fun leaveEvent(e:PlayerQuitEvent){

            quit(e.player)

        }

    }


}